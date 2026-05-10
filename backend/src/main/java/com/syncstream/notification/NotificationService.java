package com.syncstream.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.syncstream.common.JsonUtil;
import com.syncstream.observability.SyncMetrics;
import com.syncstream.realtime.RealtimePublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final JsonUtil jsonUtil;
    private final RealtimePublisher realtimePublisher;
    private final SyncMetrics metrics;

    public NotificationService(
            JdbcTemplate jdbc,
            JsonUtil jsonUtil,
            RealtimePublisher realtimePublisher,
            SyncMetrics metrics) {
        this.jdbc = jdbc;
        this.jsonUtil = jsonUtil;
        this.realtimePublisher = realtimePublisher;
        this.metrics = metrics;
    }

    @Transactional
    public NotificationResponse create(UUID userId, String type, String title, String message, Map<String, Object> metadata) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO notifications(user_id, type, title, message, metadata_json)
                VALUES (?, ?, ?, ?, ?::jsonb)
                RETURNING id
                """, UUID.class, userId, type, title, message, jsonUtil.objectToJson(metadata == null ? Map.of() : metadata));
        NotificationResponse notification = get(id);
        realtimePublisher.user(userId, "notification:new", notification);
        metrics.notificationCreated();
        return notification;
    }

    public List<NotificationResponse> list(UUID userId) {
        return jdbc.query("""
                SELECT id, user_id, type, title, message, is_read, metadata_json::text AS metadata_json, created_at
                FROM notifications
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 100
                """, (rs, rowNum) -> new NotificationResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getBoolean("is_read"),
                rs.getString("metadata_json"),
                rs.getObject("created_at", OffsetDateTime.class)), userId);
    }

    public void markRead(UUID userId, UUID id) {
        jdbc.update("UPDATE notifications SET is_read = true WHERE id = ? AND user_id = ?", id, userId);
    }

    public void markAllRead(UUID userId) {
        jdbc.update("UPDATE notifications SET is_read = true WHERE user_id = ?", userId);
    }

    public List<UUID> workspaceRecipients(UUID workspaceId, UUID exceptUserId) {
        return jdbc.query("""
                SELECT user_id
                FROM workspace_members
                WHERE workspace_id = ?
                  AND status = 'ACTIVE'
                  AND user_id IS NOT NULL
                  AND user_id <> ?
                """, (rs, rowNum) -> rs.getObject("user_id", UUID.class), workspaceId, exceptUserId);
    }

    private NotificationResponse get(UUID id) {
        return jdbc.queryForObject("""
                SELECT id, user_id, type, title, message, is_read, metadata_json::text AS metadata_json, created_at
                FROM notifications
                WHERE id = ?
                """, (rs, rowNum) -> new NotificationResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getBoolean("is_read"),
                rs.getString("metadata_json"),
                rs.getObject("created_at", OffsetDateTime.class)), id);
    }

    public record NotificationResponse(
            UUID id,
            UUID userId,
            String type,
            String title,
            String message,
            boolean read,
            String metadataJson,
            OffsetDateTime createdAt) {
    }
}
