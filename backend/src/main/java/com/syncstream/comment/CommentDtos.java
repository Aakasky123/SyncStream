package com.syncstream.comment;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public final class CommentDtos {
    private CommentDtos() {
    }

    public record CreateCommentRequest(
            String selectedText,
            Integer documentOffsetStart,
            Integer documentOffsetEnd,
            @NotBlank String content) {
    }

    public record CreateReplyRequest(@NotBlank String content) {
    }

    public record CommentResponse(
            UUID id,
            UUID documentId,
            UUID authorId,
            String authorName,
            UUID parentCommentId,
            String selectedText,
            Integer documentOffsetStart,
            Integer documentOffsetEnd,
            String content,
            boolean resolved,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }
}
