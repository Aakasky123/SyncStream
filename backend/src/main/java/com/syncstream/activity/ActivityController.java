package com.syncstream.activity;

import java.util.List;
import java.util.UUID;

import com.syncstream.auth.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/activity")
public class ActivityController {
    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public List<ActivityService.ActivityResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID workspaceId) {
        return activityService.list(principal.id(), workspaceId);
    }
}
