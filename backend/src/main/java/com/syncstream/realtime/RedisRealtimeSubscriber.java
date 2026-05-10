package com.syncstream.realtime;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncstream.observability.SyncMetrics;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRealtimeSubscriber implements MessageListener {
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final SyncMetrics metrics;

    public RedisRealtimeSubscriber(
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            SyncMetrics metrics) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            if (channel.equals("__keyevent@0__:expired") && body.startsWith("presence:document:")) {
                handlePresenceExpiry(body);
                return;
            }
            RealtimeEvent event = objectMapper.readValue(body, RealtimeEvent.class);
            if (channel.startsWith("syncstream:document:")) {
                String documentId = channel.substring("syncstream:document:".length());
                messagingTemplate.convertAndSend("/topic/documents/" + documentId, event);
            } else if (channel.startsWith("syncstream:workspace:")) {
                String workspaceId = channel.substring("syncstream:workspace:".length());
                messagingTemplate.convertAndSend("/topic/workspaces/" + workspaceId, event);
            } else if (channel.startsWith("syncstream:user:")) {
                String userId = channel.substring("syncstream:user:".length());
                messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", event);
            }
            metrics.redisEvent(event.type(), "received");
        } catch (Exception ex) {
            metrics.redisEvent("unknown", "failed");
        }
    }

    private void handlePresenceExpiry(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return;
        }
        UUID documentId = UUID.fromString(parts[2]);
        UUID userId = UUID.fromString(parts[3]);
        RealtimeEvent event = new RealtimeEvent("presence:leave", new PresenceLeavePayload(documentId, userId));
        messagingTemplate.convertAndSend("/topic/documents/" + documentId, event);
        metrics.redisEvent("presence:leave", "expired");
    }

    public record PresenceLeavePayload(UUID documentId, UUID userId) {
    }
}
