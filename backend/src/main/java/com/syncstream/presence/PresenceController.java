package com.syncstream.presence;

import static com.syncstream.presence.PresenceDtos.PresenceState;

import java.util.List;
import java.util.UUID;

import com.syncstream.auth.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}/presence")
public class PresenceController {
    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping
    public List<PresenceState> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID documentId) {
        return presenceService.list(principal.id(), documentId);
    }
}
