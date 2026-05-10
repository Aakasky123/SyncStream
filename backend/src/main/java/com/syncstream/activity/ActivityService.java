package com.syncstream.activity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.syncstream.common.JsonUtil;
import com.syncstream.workspace.MembershipService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {
    private final JdbcTemplate jdbc;
    private final JsonUtil jsonUtil;
    private final MembershipService membershipService;

    public ActivityService(JdbcTemplate jdbc, JsonUtil jsonUtil, MembershipService membershipService) {
        this.jdbc = jdbc;
        this.jsonUtil = jsonUtil;
        this.membershipService = membershipService;
    }

    public void log(UUID workspaceId, UUID documentId, UUID actorId, String action, Map<String, Object> metadata) {
        jdbc.update("""
                INSERT INTO activity_logs(workspace_id, document_id, actor_id, action, metadata_json)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """, workspaceId, documentId, actorId, action, jsonUtil.objectToJson(metadata == null ? Map.of() : metadata));
    }

    public List<ActivityResponse> list(UUID userId, UUID workspaceId) {
        membershipService.requireWorkspaceMember(userId, workspaceId);
        return jdbc.query("""
                SELECT al.id, al.workspace_id, al.document_id, al.actor_id, u.name AS actor_name,
                       al.action, al.metadata_json::text AS metadata_json, al.created_at
                FROM activity_logs al
                LEFT JOIN users u ON u.id = al.actor_id
                WHERE al.workspace_id = ?
                ORDER BY al.created_at DESC
                LIMIT 100
                """, (rs, rowNum) -> new ActivityResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_name"),
                rs.getString("action"),
                rs.getString("metadata_json"),
                rs.getObject("created_at", OffsetDateTime.class)), workspaceId);
    }

    public record ActivityResponse(
            UUID id,
            UUID workspaceId,
            UUID documentId,
            UUID actorId,
            String actorName,
            String action,
            String metadataJson,
            OffsetDateTime createdAt) {
    }
}
