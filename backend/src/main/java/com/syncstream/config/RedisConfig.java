package com.syncstream.config;

import com.syncstream.realtime.RedisRealtimeSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {
    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisRealtimeSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new PatternTopic("syncstream:document:*"));
        container.addMessageListener(subscriber, new PatternTopic("syncstream:workspace:*"));
        container.addMessageListener(subscriber, new PatternTopic("syncstream:user:*"));
        container.addMessageListener(subscriber, new PatternTopic("__keyevent@0__:expired"));
        return container;
    }
}
