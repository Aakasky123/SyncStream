package com.syncstream.presence;

import static com.syncstream.presence.PresenceDtos.PresenceHeartbeatRequest;
import static com.syncstream.presence.PresenceDtos.PresenceLeave;
import static com.syncstream.presence.PresenceDtos.PresenceState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.syncstream.realtime.RealtimePublisher;
import com.syncstream.workspace.MembershipService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(10);

    private final StringRedisTemplate redis;
    private final MembershipService membershipService;
    private final RealtimePublisher realtimePublisher;

    public PresenceService(
            StringRedisTemplate redis,
            MembershipService membershipService,
            RealtimePublisher realtimePublisher) {
        this.redis = redis;
        this.membershipService = membershipService;
        this.realtimePublisher = realtimePublisher;
    }

    public PresenceState heartbeat(UUID userId, String fallbackName, String sessionId, PresenceHeartbeatRequest request) {
        membershipService.requireDocumentAccess(userId, request.documentId());
        String connectionId = request.connectionId() == null || request.connectionId().isBlank()
                ? sessionId
                : request.connectionId();
        PresenceState state = new PresenceState(
                request.documentId(),
                userId,
                request.name() == null || request.name().isBlank() ? fallbackName : request.name(),
                request.avatarColor() == null ? "#2563eb" : request.avatarColor(),
                request.cursorX() == null ? 0 : request.cursorX(),
                request.cursorY() == null ? 0 : request.cursorY(),
                Boolean.TRUE.equals(request.isTyping()),
                Instant.now().toString(),
                connectionId);
        String key = key(request.documentId(), userId);
        redis.opsForHash().putAll(key, Map.of(
                "documentId", state.documentId().toString(),
                "userId", state.userId().toString(),
                "name", state.name(),
                "avatarColor", state.avatarColor(),
                "cursorX", state.cursorX().toString(),
                "cursorY", state.cursorY().toString(),
                "isTyping", state.isTyping().toString(),
                "lastSeen", state.lastSeen(),
                "connectionId", state.connectionId()));
        redis.expire(key, PRESENCE_TTL);
        if (sessionId != null) {
            String indexKey = connectionKey(sessionId);
            redis.opsForSet().add(indexKey, key);
            redis.expire(indexKey, Duration.ofMinutes(5));
        }
        realtimePublisher.document(request.documentId(), "presence:heartbeat", state);
        return state;
    }

    public List<PresenceState> list(UUID userId, UUID documentId) {
        membershipService.requireDocumentAccess(userId, documentId);
        List<PresenceState> states = new ArrayList<>();
        for (String key : redis.keys("presence:document:" + documentId + ":*")) {
            Map<Object, Object> values = redis.opsForHash().entries(key);
            if (!values.isEmpty()) {
                states.add(fromHash(values));
            }
        }
        return states;
    }

    public void removeConnection(String sessionId) {
        if (sessionId == null) {
            return;
        }
        String indexKey = connectionKey(sessionId);
        var keys = redis.opsForSet().members(indexKey);
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            redis.delete(key);
            String[] parts = key.split(":");
            if (parts.length == 4) {
                UUID documentId = UUID.fromString(parts[2]);
                UUID userId = UUID.fromString(parts[3]);
                realtimePublisher.document(documentId, "presence:leave", new PresenceLeave(documentId, userId));
            }
        }
        redis.delete(indexKey);
    }

    private PresenceState fromHash(Map<Object, Object> values) {
        return new PresenceState(
                UUID.fromString(value(values, "documentId")),
                UUID.fromString(value(values, "userId")),
                value(values, "name"),
                value(values, "avatarColor"),
                Integer.parseInt(value(values, "cursorX")),
                Integer.parseInt(value(values, "cursorY")),
                Boolean.parseBoolean(value(values, "isTyping")),
                value(values, "lastSeen"),
                value(values, "connectionId"));
    }

    private String value(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private String key(UUID documentId, UUID userId) {
        return "presence:document:" + documentId + ":" + userId;
    }

    private String connectionKey(String sessionId) {
        return "presence:connection:" + sessionId;
    }
}
