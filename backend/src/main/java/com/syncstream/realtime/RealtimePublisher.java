package com.syncstream.realtime;

import java.util.UUID;

import com.syncstream.common.JsonUtil;
import com.syncstream.observability.SyncMetrics;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RealtimePublisher {
    private final StringRedisTemplate redis;
    private final JsonUtil jsonUtil;
    private final SyncMetrics metrics;

    public RealtimePublisher(StringRedisTemplate redis, JsonUtil jsonUtil, SyncMetrics metrics) {
        this.redis = redis;
        this.jsonUtil = jsonUtil;
        this.metrics = metrics;
    }

    public void document(UUID documentId, String type, Object payload) {
        publish("syncstream:document:" + documentId, type, payload);
    }

    public void workspace(UUID workspaceId, String type, Object payload) {
        publish("syncstream:workspace:" + workspaceId, type, payload);
    }

    public void user(UUID userId, String type, Object payload) {
        publish("syncstream:user:" + userId, type, payload);
    }

    private void publish(String channel, String type, Object payload) {
        redis.convertAndSend(channel, jsonUtil.objectToJson(new RealtimeEvent(type, payload)));
        metrics.redisEvent(type, "published");
    }
}
