package com.syncstream.document;

import static com.syncstream.document.DocumentDtos.CreateDocumentRequest;
import static com.syncstream.document.DocumentDtos.DocumentResponse;
import static com.syncstream.document.DocumentDtos.DocumentSummary;
import static com.syncstream.document.DocumentDtos.UpdateDocumentRequest;
import static com.syncstream.document.DocumentDtos.VersionResponse;

import java.util.List;
import java.util.UUID;

import com.syncstream.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/api/workspaces/{workspaceId}/documents")
    public DocumentResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateDocumentRequest request) {
        return documentService.create(principal.id(), workspaceId, request);
    }

    @GetMapping("/api/workspaces/{workspaceId}/documents")
    public List<DocumentSummary> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID workspaceId) {
        return documentService.list(principal.id(), workspaceId);
    }

    @GetMapping("/api/documents/{documentId}")
    public DocumentResponse get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID documentId) {
        return documentService.get(principal.id(), documentId);
    }

    @PatchMapping("/api/documents/{documentId}")
    public DocumentResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID documentId,
            @RequestBody UpdateDocumentRequest request) {
        return documentService.update(principal.id(), documentId, request);
    }

    @DeleteMapping("/api/documents/{documentId}")
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID documentId) {
        documentService.delete(principal.id(), documentId);
    }

    @PostMapping("/api/documents/{documentId}/restore")
    public DocumentResponse restoreDeleted(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID documentId) {
        return documentService.restoreDeleted(principal.id(), documentId);
    }

    @PostMapping("/api/documents/{documentId}/versions")
    public VersionResponse createVersion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID documentId) {
        return documentService.createVersion(principal.id(), documentId);
    }

    @GetMapping("/api/documents/{documentId}/versions")
    public List<VersionResponse> versions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID documentId) {
        return documentService.versions(principal.id(), documentId);
    }

    @PostMapping("/api/documents/{documentId}/versions/{versionId}/restore")
    public DocumentResponse restoreVersion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID documentId,
            @PathVariable UUID versionId) {
        return documentService.restoreVersion(principal.id(), documentId, versionId);
    }
}
