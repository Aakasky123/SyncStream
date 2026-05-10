package com.syncstream.notification;

import java.util.List;
import java.util.UUID;

import com.syncstream.auth.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationService.NotificationResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.list(principal.id());
    }

    @PatchMapping("/{id}/read")
    public void read(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        notificationService.markRead(principal.id(), id);
    }

    @PatchMapping("/read-all")
    public void readAll(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.id());
    }
}
