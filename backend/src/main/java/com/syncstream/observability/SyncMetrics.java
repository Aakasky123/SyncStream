package com.syncstream.observability;

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class SyncMetrics {
    private final MeterRegistry registry;
    private final AtomicInteger activeConnections = new AtomicInteger();

    public SyncMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("syncstream_active_websocket_connections", activeConnections);
    }

    public void websocketConnected() {
        activeConnections.incrementAndGet();
    }

    public void websocketDisconnected() {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
        Counter.builder("syncstream_websocket_disconnect_total").register(registry).increment();
    }

    public void documentPatch(String workspaceId, String documentId, String status) {
        Counter.builder("syncstream_document_patch_events_total")
                .tags(Tags.of("workspace_id", workspaceId, "document_id", documentId, "status", status))
                .register(registry)
                .increment();
    }

    public void conflict(String workspaceId, String documentId) {
        Counter.builder("syncstream_document_conflicts_total")
                .tags(Tags.of("workspace_id", workspaceId, "document_id", documentId))
                .register(registry)
                .increment();
    }

    public Timer.Sample autosaveStarted() {
        Counter.builder("syncstream_autosave_requests_total").register(registry).increment();
        return Timer.start(registry);
    }

    public void autosaveFinished(Timer.Sample sample, String workspaceId, String documentId, String status) {
        sample.stop(Timer.builder("syncstream_autosave_latency_ms")
                .tags(Tags.of("workspace_id", workspaceId, "document_id", documentId, "status", status))
                .register(registry));
    }

    public void redisEvent(String eventType, String status) {
        Counter.builder("syncstream_redis_pubsub_events_total")
                .tags(Tags.of("event_type", eventType, "status", status))
                .register(registry)
                .increment();
    }

    public void notificationCreated() {
        Counter.builder("syncstream_notifications_created_total").register(registry).increment();
    }
}
