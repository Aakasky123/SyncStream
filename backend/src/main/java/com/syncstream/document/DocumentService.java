package com.syncstream.document;

import static com.syncstream.document.DocumentDtos.CreateDocumentRequest;
import static com.syncstream.document.DocumentDtos.DocumentConflictEvent;
import static com.syncstream.document.DocumentDtos.DocumentPatchRequest;
import static com.syncstream.document.DocumentDtos.DocumentResponse;
import static com.syncstream.document.DocumentDtos.DocumentSavedEvent;
import static com.syncstream.document.DocumentDtos.DocumentSummary;
import static com.syncstream.document.DocumentDtos.UpdateDocumentRequest;
import static com.syncstream.document.DocumentDtos.VersionResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncstream.activity.ActivityService;
import com.syncstream.common.ApiException;
import com.syncstream.common.HashUtil;
import com.syncstream.common.JsonUtil;
import com.syncstream.observability.SyncMetrics;
import com.syncstream.realtime.RealtimePublisher;
import com.syncstream.workspace.MembershipService;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final JsonUtil jsonUtil;
    private final MembershipService membershipService;
    private final ActivityService activityService;
    private final RealtimePublisher realtimePublisher;
    private final SyncMetrics metrics;
    private final String instanceId;

    public DocumentService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            JsonUtil jsonUtil,
            MembershipService membershipService,
            ActivityService activityService,
            RealtimePublisher realtimePublisher,
            SyncMetrics metrics,
            @Value("${syncstream.instance-id}") String instanceId) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.jsonUtil = jsonUtil;
        this.membershipService = membershipService;
        this.activityService = activityService;
        this.realtimePublisher = realtimePublisher;
        this.metrics = metrics;
        this.instanceId = instanceId;
    }

    @Transactional
    public DocumentResponse create(UUID userId, UUID workspaceId, CreateDocumentRequest request) {
        membershipService.requireWorkspaceMember(userId, workspaceId);
        Object content = jsonUtil.emptyDocumentNode();
        String contentJson = jsonUtil.canonical(content);
        String hash = HashUtil.sha256(contentJson);
        UUID documentId = jdbc.queryForObject("""
                INSERT INTO documents(workspace_id, title, content, version, content_hash, last_saved_hash, created_by, updated_by)
                VALUES (?, ?, ?::jsonb, 1, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, workspaceId, request.title(), contentJson, hash, hash, userId, userId);
        activityService.log(workspaceId, documentId, userId, "document.created", Map.of("title", request.title()));
        return get(userId, documentId);
    }

    public List<DocumentSummary> list(UUID userId, UUID workspaceId) {
        membershipService.requireWorkspaceMember(userId, workspaceId);
        return jdbc.query("""
                SELECT id, workspace_id, title, version, created_at, updated_at
                FROM documents
                WHERE workspace_id = ?
                  AND deleted_at IS NULL
                ORDER BY updated_at DESC
                """, (rs, rowNum) -> new DocumentSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("title"),
                rs.getInt("version"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)), workspaceId);
    }

    public DocumentResponse get(UUID userId, UUID documentId) {
        membershipService.requireDocumentAccess(userId, documentId);
        return findDocument(documentId);
    }

    @Transactional
    public DocumentResponse update(UUID userId, UUID documentId, UpdateDocumentRequest request) {
        membershipService.requireDocumentAccess(userId, documentId);
        DocumentRow row = lockDocument(documentId);
        String title = request.title() == null || request.title().isBlank() ? row.title() : request.title();
        Object content = request.content() == null ? row.content() : request.content();
        if (request.expectedVersion() != null && request.expectedVersion() < row.version()) {
            throw new ApiException(HttpStatus.CONFLICT, "Document has a newer server version");
        }
        String contentJson = jsonUtil.canonical(content);
        String hash = HashUtil.sha256(contentJson);
        int nextVersion = hash.equals(row.contentHash()) && title.equals(row.title()) ? row.version() : row.version() + 1;
        jdbc.update("""
                UPDATE documents
                SET title = ?, content = ?::jsonb, content_hash = ?, last_saved_hash = ?,
                    version = ?, updated_by = ?, updated_at = now()
                WHERE id = ?
                """, title, contentJson, hash, hash, nextVersion, userId, documentId);
        DocumentResponse updated = findDocument(documentId);
        realtimePublisher.document(documentId, "document:saved", new DocumentSavedEvent(
                documentId,
                updated.workspaceId(),
                updated.version(),
                updated.content(),
                null,
                updated.contentHash(),
                userId,
                "rest",
                null,
                instanceId));
        activityService.log(updated.workspaceId(), documentId, userId, "document.updated", Map.of("version", updated.version()));
        return updated;
    }

    @Transactional
    public PatchOutcome applyPatch(UUID userId, UUID documentId, DocumentPatchRequest request) {
        membershipService.requireDocumentAccess(userId, documentId);
        Timer.Sample sample = metrics.autosaveStarted();
        DocumentRow row = lockDocument(documentId);
        if (request.baseVersion() < row.version()) {
            metrics.conflict(row.workspaceId().toString(), documentId.toString());
            metrics.documentPatch(row.workspaceId().toString(), documentId.toString(), "conflict");
            metrics.autosaveFinished(sample, row.workspaceId().toString(), documentId.toString(), "conflict");
            return PatchOutcome.conflict(new DocumentConflictEvent(
                    "document:conflict",
                    documentId,
                    row.version(),
                    row.content(),
                    request.baseVersion(),
                    request.content()));
        }
        String contentJson = jsonUtil.canonical(request.content());
        String hash = HashUtil.sha256(contentJson);
        int nextVersion = hash.equals(row.contentHash()) ? row.version() : row.version() + 1;
        if (!hash.equals(row.lastSavedHash())) {
            jdbc.update("""
                    UPDATE documents
                    SET content = ?::jsonb, content_hash = ?, last_saved_hash = ?,
                        version = ?, updated_by = ?, updated_at = now()
                    WHERE id = ?
                    """, contentJson, hash, hash, nextVersion, userId, documentId);
        }
        DocumentSavedEvent event = new DocumentSavedEvent(
                documentId,
                row.workspaceId(),
                nextVersion,
                request.content(),
                request.steps(),
                hash,
                userId,
                request.clientId(),
                request.clientSeq(),
                instanceId);
        realtimePublisher.document(documentId, "document:saved", event);
        activityService.log(row.workspaceId(), documentId, userId, "document.autosaved", Map.of("version", nextVersion));
        metrics.documentPatch(row.workspaceId().toString(), documentId.toString(), "accepted");
        metrics.autosaveFinished(sample, row.workspaceId().toString(), documentId.toString(), "accepted");
        return PatchOutcome.saved(event);
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        membershipService.requireDocumentAccess(userId, documentId);
        UUID workspaceId = membershipService.workspaceIdForDocument(documentId);
        jdbc.update("UPDATE documents SET deleted_at = now(), updated_by = ?, updated_at = now() WHERE id = ?", userId, documentId);
        activityService.log(workspaceId, documentId, userId, "document.deleted", Map.of());
    }

    @Transactional
    public DocumentResponse restoreDeleted(UUID userId, UUID documentId) {
        UUID workspaceId = membershipService.workspaceIdForDocument(documentId);
        membershipService.requireWorkspaceMember(userId, workspaceId);
        jdbc.update("UPDATE documents SET deleted_at = NULL, updated_by = ?, updated_at = now() WHERE id = ?", userId, documentId);
        activityService.log(workspaceId, documentId, userId, "document.restored", Map.of());
        return findDocument(documentId);
    }

    @Transactional
    public VersionResponse createVersion(UUID userId, UUID documentId) {
        membershipService.requireDocumentAccess(userId, documentId);
        DocumentRow row = lockDocument(documentId);
        UUID versionId = jdbc.queryForObject("""
                INSERT INTO document_versions(document_id, version_number, content, content_hash, created_by)
                VALUES (?, ?, ?::jsonb, ?, ?)
                RETURNING id
                """, UUID.class, documentId, row.version(), jsonUtil.canonical(row.content()), row.contentHash(), userId);
        activityService.log(row.workspaceId(), documentId, userId, "document.version.created", Map.of("version", row.version()));
        return version(versionId);
    }

    public List<VersionResponse> versions(UUID userId, UUID documentId) {
        membershipService.requireDocumentAccess(userId, documentId);
        return jdbc.query("""
                SELECT id, document_id, version_number, content::text AS content, content_hash, created_by, created_at
                FROM document_versions
                WHERE document_id = ?
                ORDER BY created_at DESC
                """, (rs, rowNum) -> new VersionResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getInt("version_number"),
                readJson(rs.getString("content")),
                rs.getString("content_hash"),
                rs.getObject("created_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class)), documentId);
    }

    @Transactional
    public DocumentResponse restoreVersion(UUID userId, UUID documentId, UUID versionId) {
        membershipService.requireDocumentAccess(userId, documentId);
        DocumentRow row = lockDocument(documentId);
        VersionResponse version = version(versionId);
        if (!version.documentId().equals(documentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Version does not belong to document");
        }
        int nextVersion = row.version() + 1;
        jdbc.update("""
                UPDATE documents
                SET content = ?::jsonb, content_hash = ?, last_saved_hash = ?, version = ?,
                    updated_by = ?, updated_at = now()
                WHERE id = ?
                """, jsonUtil.canonical(version.content()), version.contentHash(), version.contentHash(), nextVersion, userId, documentId);
        DocumentResponse updated = findDocument(documentId);
        realtimePublisher.document(documentId, "document:saved", new DocumentSavedEvent(
                documentId,
                row.workspaceId(),
                nextVersion,
                updated.content(),
                null,
                updated.contentHash(),
                userId,
                "restore",
                null,
                instanceId));
        activityService.log(row.workspaceId(), documentId, userId, "document.version.restored", Map.of("versionId", versionId.toString()));
        return updated;
    }

    private VersionResponse version(UUID versionId) {
        var rows = jdbc.query("""
                SELECT id, document_id, version_number, content::text AS content, content_hash, created_by, created_at
                FROM document_versions
                WHERE id = ?
                """, (rs, rowNum) -> new VersionResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getInt("version_number"),
                readJson(rs.getString("content")),
                rs.getString("content_hash"),
                rs.getObject("created_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class)), versionId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Version not found");
        }
        return rows.get(0);
    }

    private DocumentResponse findDocument(UUID documentId) {
        var rows = jdbc.query("""
                SELECT id, workspace_id, title, content::text AS content, version, content_hash,
                       last_saved_hash, created_by, updated_by, created_at, updated_at
                FROM documents
                WHERE id = ?
                  AND deleted_at IS NULL
                """, (rs, rowNum) -> new DocumentResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("title"),
                readJson(rs.getString("content")),
                rs.getInt("version"),
                rs.getString("content_hash"),
                rs.getString("last_saved_hash"),
                rs.getObject("created_by", UUID.class),
                rs.getObject("updated_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)), documentId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return rows.get(0);
    }

    private DocumentRow lockDocument(UUID documentId) {
        var rows = jdbc.query("""
                SELECT id, workspace_id, title, content::text AS content, version, content_hash, last_saved_hash
                FROM documents
                WHERE id = ?
                  AND deleted_at IS NULL
                FOR UPDATE
                """, (rs, rowNum) -> new DocumentRow(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("title"),
                readJson(rs.getString("content")),
                rs.getInt("version"),
                rs.getString("content_hash"),
                rs.getString("last_saved_hash")), documentId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return rows.get(0);
    }

    private Object readJson(String value) {
        try {
            return mapper.readValue(value, Object.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored document JSON is invalid", ex);
        }
    }

    private record DocumentRow(
            UUID id,
            UUID workspaceId,
            String title,
            Object content,
            Integer version,
            String contentHash,
            String lastSavedHash) {
    }

    public record PatchOutcome(boolean conflict, DocumentSavedEvent savedEvent, DocumentConflictEvent conflictEvent) {
        public static PatchOutcome saved(DocumentSavedEvent event) {
            return new PatchOutcome(false, event, null);
        }

        public static PatchOutcome conflict(DocumentConflictEvent event) {
            return new PatchOutcome(true, null, event);
        }
    }
}
