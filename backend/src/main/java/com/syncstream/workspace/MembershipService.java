package com.syncstream.workspace;

import java.util.List;
import java.util.UUID;

import com.syncstream.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MembershipService {
    private final JdbcTemplate jdbc;

    public MembershipService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireWorkspaceMember(UUID userId, UUID workspaceId) {
        if (!isWorkspaceMember(userId, workspaceId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Workspace access denied");
        }
    }

    public void requireDocumentAccess(UUID userId, UUID documentId) {
        if (!canAccessDocument(userId, documentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Document access denied");
        }
    }

    public boolean isWorkspaceMember(UUID userId, UUID workspaceId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                FROM workspace_members
                WHERE workspace_id = ?
                  AND user_id = ?
                  AND status = 'ACTIVE'
                """, Integer.class, workspaceId, userId);
        return count != null && count > 0;
    }

    public boolean canAccessDocument(UUID userId, UUID documentId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                FROM documents d
                JOIN workspace_members wm ON wm.workspace_id = d.workspace_id
                WHERE d.id = ?
                  AND d.deleted_at IS NULL
                  AND wm.user_id = ?
                  AND wm.status = 'ACTIVE'
                """, Integer.class, documentId, userId);
        return count != null && count > 0;
    }

    public UUID workspaceIdForDocument(UUID documentId) {
        List<UUID> rows = jdbc.query("""
                SELECT workspace_id FROM documents WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> rs.getObject("workspace_id", UUID.class), documentId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return rows.get(0);
    }
}
