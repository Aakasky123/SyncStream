package com.syncstream.document;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class DocumentDtos {
    private DocumentDtos() {
    }

    public record CreateDocumentRequest(@NotBlank String title) {
    }

    public record UpdateDocumentRequest(String title, Object content, Integer expectedVersion) {
    }

    public record DocumentPatchRequest(
            @NotNull Integer baseVersion,
            Object steps,
            @NotNull Object content,
            String contentHash,
            String clientId,
            Long clientSeq,
            String clientUpdatedAt) {
    }

    public record DocumentResponse(
            UUID id,
            UUID workspaceId,
            String title,
            Object content,
            Integer version,
            String contentHash,
            String lastSavedHash,
            UUID createdBy,
            UUID updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record DocumentSummary(
            UUID id,
            UUID workspaceId,
            String title,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record DocumentSavedEvent(
            UUID documentId,
            UUID workspaceId,
            Integer version,
            Object content,
            Object steps,
            String contentHash,
            UUID updatedBy,
            String clientId,
            Long clientSeq,
            String instanceId) {
    }

    public record DocumentConflictEvent(
            String type,
            UUID documentId,
            Integer serverVersion,
            Object serverContent,
            Integer clientVersion,
            Object clientContent) {
    }

    public record VersionResponse(
            UUID id,
            UUID documentId,
            Integer versionNumber,
            Object content,
            String contentHash,
            UUID createdBy,
            OffsetDateTime createdAt) {
    }
}
