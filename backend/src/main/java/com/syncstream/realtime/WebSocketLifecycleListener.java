package com.syncstream.realtime;

import com.syncstream.observability.SyncMetrics;
import com.syncstream.presence.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketLifecycleListener {
    private final SyncMetrics metrics;
    private final PresenceService presenceService;

    public WebSocketLifecycleListener(SyncMetrics metrics, PresenceService presenceService) {
        this.metrics = metrics;
        this.presenceService = presenceService;
    }

    @EventListener
    public void connected(SessionConnectEvent event) {
        metrics.websocketConnected();
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        metrics.websocketDisconnected();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        presenceService.removeConnection(accessor.getSessionId());
    }
}
