package dev.shortline;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfiguration {
    @Bean
    NewTopic clickEventsTopic() {
        return TopicBuilder.name(ClickEventPublisher.TOPIC).partitions(3).replicas(1).build();
    }
}
