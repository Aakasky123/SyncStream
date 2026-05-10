package com.syncstream.workspace;

import static com.syncstream.workspace.WorkspaceDtos.AcceptInvitationResponse;
import static com.syncstream.workspace.WorkspaceDtos.CreateWorkspaceRequest;
import static com.syncstream.workspace.WorkspaceDtos.InvitationResponse;
import static com.syncstream.workspace.WorkspaceDtos.InviteRequest;
import static com.syncstream.workspace.WorkspaceDtos.MemberResponse;
import static com.syncstream.workspace.WorkspaceDtos.WorkspaceResponse;

import java.util.List;
import java.util.UUID;

import com.syncstream.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public WorkspaceResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        return workspaceService.create(principal.id(), request);
    }

    @GetMapping
    public List<WorkspaceResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return workspaceService.list(principal.id());
    }

    @GetMapping("/{id}")
    public WorkspaceResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return workspaceService.get(principal.id(), id);
    }

    @PostMapping("/{id}/invite")
    public InvitationResponse invite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody InviteRequest request) {
        return workspaceService.invite(principal.id(), id, request);
    }

    @PostMapping("/invitations/{token}/accept")
    public AcceptInvitationResponse accept(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String token) {
        return workspaceService.accept(principal.id(), token);
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> members(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return workspaceService.members(principal.id(), id);
    }
}
