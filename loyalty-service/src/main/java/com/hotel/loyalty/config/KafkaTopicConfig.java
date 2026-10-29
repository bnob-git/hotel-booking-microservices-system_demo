package com.hotel.loyalty.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares booking-events with the same 3 partitions as booking-service, so a consumer that starts first does not
 * auto-create a 1-partition topic and miss partitions added later.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic bookingEventsTopic(@Value("${loyalty.events.topic:booking-events}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
