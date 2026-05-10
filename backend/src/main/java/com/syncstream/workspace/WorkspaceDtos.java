package com.syncstream.workspace;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class WorkspaceDtos {
    private WorkspaceDtos() {
    }

    public record CreateWorkspaceRequest(@NotBlank String name) {
    }

    public record InviteRequest(@Email @NotBlank String email, String role) {
    }

    public record AcceptInvitationResponse(UUID workspaceId, String workspaceName, String status) {
    }

    public record InvitationResponse(
            UUID id,
            UUID workspaceId,
            String email,
            String role,
            String status,
            String invitationToken,
            String acceptUrl,
            OffsetDateTime invitationExpiresAt) {
    }

    public record WorkspaceResponse(
            UUID id,
            String name,
            UUID ownerId,
            String role,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record MemberResponse(
            UUID id,
            UUID userId,
            String name,
            String email,
            String role,
            String status,
            OffsetDateTime joinedAt) {
    }
}
