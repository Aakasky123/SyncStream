package com.syncstream.document;

import static com.syncstream.document.DocumentDtos.DocumentPatchRequest;
import static com.syncstream.presence.PresenceDtos.PresenceHeartbeatRequest;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import com.syncstream.auth.UserPrincipal;
import com.syncstream.common.ApiException;
import com.syncstream.presence.PresenceService;
import com.syncstream.realtime.RealtimeEvent;
import com.syncstream.workspace.MembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

@Controller
public class DocumentSocketController {
    private final DocumentService documentService;
    private final MembershipService membershipService;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    public DocumentSocketController(
            DocumentService documentService,
            MembershipService membershipService,
            PresenceService presenceService,
            SimpMessagingTemplate messagingTemplate) {
        this.documentService = documentService;
        this.membershipService = membershipService;
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/documents/{documentId}/join")
    public void join(@DestinationVariable UUID documentId, Principal principal) {
        UserPrincipal user = user(principal);
        membershipService.requireDocumentAccess(user.id(), documentId);
        messagingTemplate.convertAndSend("/topic/documents/" + documentId,
                new RealtimeEvent("document:join", Map.of("documentId", documentId, "userId", user.id())));
    }

    @MessageMapping("/documents/{documentId}/leave")
    public void leave(@DestinationVariable UUID documentId, Principal principal) {
        UserPrincipal user = user(principal);
        messagingTemplate.convertAndSend("/topic/documents/" + documentId,
                new RealtimeEvent("document:leave", Map.of("documentId", documentId, "userId", user.id())));
    }

    @MessageMapping("/documents/{documentId}/patch")
    public void patch(
            @DestinationVariable UUID documentId,
            DocumentPatchRequest request,
            Principal principal) {
        UserPrincipal user = user(principal);
        DocumentService.PatchOutcome outcome = documentService.applyPatch(user.id(), documentId, request);
        if (outcome.conflict()) {
            messagingTemplate.convertAndSendToUser(
                    user.id().toString(),
                    "/queue/documents/" + documentId,
                    new RealtimeEvent("document:conflict", outcome.conflictEvent()));
        }
    }

    @MessageMapping("/presence/heartbeat")
    public void presence(
            PresenceHeartbeatRequest request,
            Principal principal,
            @Header(name = "simpSessionId", required = false) String sessionId) {
        UserPrincipal user = user(principal);
        presenceService.heartbeat(user.id(), user.displayName(), sessionId, request);
    }

    private UserPrincipal user(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof UserPrincipal user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "WebSocket authentication required");
    }
}
