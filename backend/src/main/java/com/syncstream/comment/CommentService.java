package com.syncstream.comment;

import static com.syncstream.comment.CommentDtos.CommentResponse;
import static com.syncstream.comment.CommentDtos.CreateCommentRequest;
import static com.syncstream.comment.CommentDtos.CreateReplyRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.syncstream.activity.ActivityService;
import com.syncstream.notification.NotificationService;
import com.syncstream.realtime.RealtimePublisher;
import com.syncstream.workspace.MembershipService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
    private final JdbcTemplate jdbc;
    private final MembershipService membershipService;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;
    private final ActivityService activityService;

    public CommentService(
            JdbcTemplate jdbc,
            MembershipService membershipService,
            NotificationService notificationService,
            RealtimePublisher realtimePublisher,
            ActivityService activityService) {
        this.jdbc = jdbc;
        this.membershipService = membershipService;
        this.notificationService = notificationService;
        this.realtimePublisher = realtimePublisher;
        this.activityService = activityService;
    }

    @Transactional
    public CommentResponse create(UUID userId, UUID documentId, CreateCommentRequest request) {
        membershipService.requireDocumentAccess(userId, documentId);
        UUID id = jdbc.queryForObject("""
                INSERT INTO comments(document_id, author_id, selected_text, document_offset_start, document_offset_end, content)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, documentId, userId, request.selectedText(), request.documentOffsetStart(),
                request.documentOffsetEnd(), request.content());
        CommentResponse comment = get(id);
        UUID workspaceId = membershipService.workspaceIdForDocument(documentId);
        activityService.log(workspaceId, documentId, userId, "comment.created", Map.of("commentId", id.toString()));
        realtimePublisher.document(documentId, "comment:new", comment);
        notifyWorkspace(workspaceId, userId, documentId, "New comment", comment.content());
        return comment;
    }

    @Transactional
    public CommentResponse reply(UUID userId, UUID parentCommentId, CreateReplyRequest request) {
        CommentResponse parent = get(parentCommentId);
        membershipService.requireDocumentAccess(userId, parent.documentId());
        UUID id = jdbc.queryForObject("""
                INSERT INTO comments(document_id, author_id, parent_comment_id, content)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, UUID.class, parent.documentId(), userId, parentCommentId, request.content());
        CommentResponse reply = get(id);
        UUID workspaceId = membershipService.workspaceIdForDocument(parent.documentId());
        activityService.log(workspaceId, parent.documentId(), userId, "comment.replied", Map.of("commentId", id.toString()));
        realtimePublisher.document(parent.documentId(), "comment:new", reply);
        notifyWorkspace(workspaceId, userId, parent.documentId(), "New reply", reply.content());
        return reply;
    }

    public List<CommentResponse> list(UUID userId, UUID documentId) {
        membershipService.requireDocumentAccess(userId, documentId);
        return jdbc.query("""
                SELECT c.id, c.document_id, c.author_id, u.name AS author_name, c.parent_comment_id,
                       c.selected_text, c.document_offset_start, c.document_offset_end, c.content,
                       c.is_resolved, c.created_at, c.updated_at
                FROM comments c
                JOIN users u ON u.id = c.author_id
                WHERE c.document_id = ?
                ORDER BY c.created_at ASC
                """, (rs, rowNum) -> mapComment(rs), documentId);
    }

    @Transactional
    public CommentResponse resolve(UUID userId, UUID commentId) {
        CommentResponse comment = get(commentId);
        membershipService.requireDocumentAccess(userId, comment.documentId());
        jdbc.update("UPDATE comments SET is_resolved = true, updated_at = now() WHERE id = ?", commentId);
        UUID workspaceId = membershipService.workspaceIdForDocument(comment.documentId());
        activityService.log(workspaceId, comment.documentId(), userId, "comment.resolved", Map.of("commentId", commentId.toString()));
        return get(commentId);
    }

    private void notifyWorkspace(UUID workspaceId, UUID authorId, UUID documentId, String title, String message) {
        for (UUID recipient : notificationService.workspaceRecipients(workspaceId, authorId)) {
            notificationService.create(recipient, "COMMENT", title, message, Map.of(
                    "workspaceId", workspaceId.toString(),
                    "documentId", documentId.toString()));
        }
    }

    private CommentResponse get(UUID id) {
        return jdbc.queryForObject("""
                SELECT c.id, c.document_id, c.author_id, u.name AS author_name, c.parent_comment_id,
                       c.selected_text, c.document_offset_start, c.document_offset_end, c.content,
                       c.is_resolved, c.created_at, c.updated_at
                FROM comments c
                JOIN users u ON u.id = c.author_id
                WHERE c.id = ?
                """, (rs, rowNum) -> mapComment(rs), id);
    }

    private CommentResponse mapComment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CommentResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getString("author_name"),
                rs.getObject("parent_comment_id", UUID.class),
                rs.getString("selected_text"),
                (Integer) rs.getObject("document_offset_start"),
                (Integer) rs.getObject("document_offset_end"),
                rs.getString("content"),
                rs.getBoolean("is_resolved"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
