package com.syncstream.workspace;

import static com.syncstream.workspace.WorkspaceDtos.AcceptInvitationResponse;
import static com.syncstream.workspace.WorkspaceDtos.CreateWorkspaceRequest;
import static com.syncstream.workspace.WorkspaceDtos.InvitationResponse;
import static com.syncstream.workspace.WorkspaceDtos.InviteRequest;
import static com.syncstream.workspace.WorkspaceDtos.MemberResponse;
import static com.syncstream.workspace.WorkspaceDtos.WorkspaceResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.syncstream.activity.ActivityService;
import com.syncstream.common.ApiException;
import com.syncstream.common.HashUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {
    private final JdbcTemplate jdbc;
    private final MembershipService membershipService;
    private final ActivityService activityService;
    private final String frontendOrigin;

    public WorkspaceService(
            JdbcTemplate jdbc,
            MembershipService membershipService,
            ActivityService activityService,
            @Value("${syncstream.frontend-origin}") String frontendOrigin) {
        this.jdbc = jdbc;
        this.membershipService = membershipService;
        this.activityService = activityService;
        this.frontendOrigin = frontendOrigin;
    }

    @Transactional
    public WorkspaceResponse create(UUID userId, CreateWorkspaceRequest request) {
        UUID workspaceId = jdbc.queryForObject("""
                INSERT INTO workspaces(name, owner_id)
                VALUES (?, ?)
                RETURNING id
                """, UUID.class, request.name(), userId);
        jdbc.update("""
                INSERT INTO workspace_members(workspace_id, user_id, role, status, joined_at)
                VALUES (?, ?, 'OWNER', 'ACTIVE', now())
                """, workspaceId, userId);
        activityService.log(workspaceId, null, userId, "workspace.created", Map.of("name", request.name()));
        return get(userId, workspaceId);
    }

    public List<WorkspaceResponse> list(UUID userId) {
        return jdbc.query("""
                SELECT w.id, w.name, w.owner_id, wm.role, w.created_at, w.updated_at
                FROM workspaces w
                JOIN workspace_members wm ON wm.workspace_id = w.id
                WHERE wm.user_id = ?
                  AND wm.status = 'ACTIVE'
                ORDER BY w.created_at DESC
                """, (rs, rowNum) -> new WorkspaceResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("owner_id", UUID.class),
                rs.getString("role"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)), userId);
    }

    public WorkspaceResponse get(UUID userId, UUID workspaceId) {
        membershipService.requireWorkspaceMember(userId, workspaceId);
        var rows = jdbc.query("""
                SELECT w.id, w.name, w.owner_id, wm.role, w.created_at, w.updated_at
                FROM workspaces w
                JOIN workspace_members wm ON wm.workspace_id = w.id AND wm.user_id = ?
                WHERE w.id = ?
                """, (rs, rowNum) -> new WorkspaceResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("owner_id", UUID.class),
                rs.getString("role"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)), userId, workspaceId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        return rows.get(0);
    }

    @Transactional
    public InvitationResponse invite(UUID userId, UUID workspaceId, InviteRequest request) {
        membershipService.requireWorkspaceMember(userId, workspaceId);
        var existing = jdbc.query("""
                SELECT id, invitation_expires_at
                FROM workspace_members
                WHERE workspace_id = ?
                  AND lower(email) = lower(?)
                  AND status = 'PENDING'
                  AND invitation_token IS NOT NULL
                ORDER BY created_at DESC
                LIMIT 1
                """, (rs, rowNum) -> new PendingInvite(
                rs.getObject("id", UUID.class),
                rs.getObject("invitation_expires_at", OffsetDateTime.class)), workspaceId, request.email());
        if (!existing.isEmpty()) {
            PendingInvite invite = existing.get(0);
            if (invite.expiresAt() != null && invite.expiresAt().isAfter(OffsetDateTime.now())) {
                return invitation(invite.id());
            }
            String token = HashUtil.randomToken();
            jdbc.update("""
                    UPDATE workspace_members
                    SET role = ?, invitation_token = ?, invitation_expires_at = now() + interval '7 days'
                    WHERE id = ?
                    """, request.role() == null ? "MEMBER" : request.role(), token, invite.id());
            return invitation(invite.id());
        }

        String token = HashUtil.randomToken();
        UUID memberId;
        try {
            memberId = jdbc.queryForObject("""
                    INSERT INTO workspace_members(workspace_id, email, role, status, invitation_token, invitation_expires_at)
                    VALUES (?, lower(?), ?, 'PENDING', ?, now() + interval '7 days')
                    RETURNING id
                    """, UUID.class, workspaceId, request.email(), request.role() == null ? "MEMBER" : request.role(), token);
        } catch (DuplicateKeyException ex) {
            return invitationByWorkspaceEmail(workspaceId, request.email());
        }
        activityService.log(workspaceId, null, userId, "workspace.invited", Map.of("email", request.email()));
        return invitation(memberId);
    }

    @Transactional
    public AcceptInvitationResponse accept(UUID userId, String token) {
        var rows = jdbc.query("""
                SELECT wm.id, wm.workspace_id, w.name
                FROM workspace_members wm
                JOIN workspaces w ON w.id = wm.workspace_id
                WHERE wm.invitation_token = ?
                  AND wm.status = 'PENDING'
                  AND wm.invitation_expires_at > now()
                """, (rs, rowNum) -> new InviteRow(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("name")), token);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Invitation not found or expired");
        }
        InviteRow row = rows.get(0);
        jdbc.update("""
                UPDATE workspace_members
                SET user_id = ?, status = 'ACTIVE', joined_at = now(), invitation_token = NULL
                WHERE id = ?
                """, userId, row.memberId());
        activityService.log(row.workspaceId(), null, userId, "workspace.joined", Map.of());
        return new AcceptInvitationResponse(row.workspaceId(), row.workspaceName(), "ACTIVE");
    }

    public List<MemberResponse> members(UUID userId, UUID workspaceId) {
        membershipService.requireWorkspaceMember(userId, workspaceId);
        return jdbc.query("""
                SELECT wm.id, wm.user_id, u.name, COALESCE(u.email, wm.email) AS email,
                       wm.role, wm.status, wm.joined_at
                FROM workspace_members wm
                LEFT JOIN users u ON u.id = wm.user_id
                WHERE wm.workspace_id = ?
                ORDER BY wm.created_at
                """, (rs, rowNum) -> new MemberResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getObject("joined_at", OffsetDateTime.class)), workspaceId);
    }

    private InvitationResponse invitation(UUID id) {
        var rows = jdbc.query("""
                SELECT id, workspace_id, email, role, status, invitation_token, invitation_expires_at
                FROM workspace_members
                WHERE id = ?
                """, (rs, rowNum) -> new InvitationResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getString("invitation_token"),
                frontendOrigin + "/?invite=" + rs.getString("invitation_token"),
                rs.getObject("invitation_expires_at", OffsetDateTime.class)), id);
        return rows.get(0);
    }

    private InvitationResponse invitationByWorkspaceEmail(UUID workspaceId, String email) {
        var rows = jdbc.query("""
                SELECT id
                FROM workspace_members
                WHERE workspace_id = ?
                  AND lower(email) = lower(?)
                  AND status = 'PENDING'
                  AND invitation_token IS NOT NULL
                ORDER BY created_at DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), workspaceId, email);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Pending invitation already exists");
        }
        return invitation(rows.get(0));
    }

    private record InviteRow(UUID memberId, UUID workspaceId, String workspaceName) {
    }

    private record PendingInvite(UUID id, OffsetDateTime expiresAt) {
    }
}
