package com.syncstream.presence;

import java.util.UUID;

public final class PresenceDtos {
    private PresenceDtos() {
    }

    public record PresenceHeartbeatRequest(
            UUID documentId,
            String name,
            String avatarColor,
            Integer cursorX,
            Integer cursorY,
            Boolean isTyping,
            String connectionId) {
    }

    public record PresenceState(
            UUID documentId,
            UUID userId,
            String name,
            String avatarColor,
            Integer cursorX,
            Integer cursorY,
            Boolean isTyping,
            String lastSeen,
            String connectionId) {
    }

    public record PresenceLeave(UUID documentId, UUID userId) {
    }
}
