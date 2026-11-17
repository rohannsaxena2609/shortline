package dev.shortline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClickAnalyticsConsumer {
    private final ClickEventRepository events;
    private final ObjectMapper objectMapper;

    public ClickAnalyticsConsumer(ClickEventRepository events, ObjectMapper objectMapper) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = ClickEventPublisher.TOPIC)
    @Transactional
    public void consume(String payload) throws Exception {
        var event = objectMapper.readValue(payload, ClickEventPublisher.ClickMessage.class);
        if (events.existsById(event.eventId())) return;
        events.saveAndFlush(new ClickEvent(event.eventId(), event.code(), event.occurredAt(), event.referrerDomain()));
    }
}
