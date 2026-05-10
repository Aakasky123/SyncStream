package com.syncstream.comment;

import static com.syncstream.comment.CommentDtos.CommentResponse;
import static com.syncstream.comment.CommentDtos.CreateCommentRequest;
import static com.syncstream.comment.CommentDtos.CreateReplyRequest;

import java.util.List;
import java.util.UUID;

import com.syncstream.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/api/documents/{documentId}/comments")
    public CommentResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID documentId,
            @Valid @RequestBody CreateCommentRequest request) {
        return commentService.create(principal.id(), documentId, request);
    }

    @GetMapping("/api/documents/{documentId}/comments")
    public List<CommentResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID documentId) {
        return commentService.list(principal.id(), documentId);
    }

    @PostMapping("/api/comments/{commentId}/replies")
    public CommentResponse reply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID commentId,
            @Valid @RequestBody CreateReplyRequest request) {
        return commentService.reply(principal.id(), commentId, request);
    }

    @PatchMapping("/api/comments/{commentId}/resolve")
    public CommentResponse resolve(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID commentId) {
        return commentService.resolve(principal.id(), commentId);
    }
}
