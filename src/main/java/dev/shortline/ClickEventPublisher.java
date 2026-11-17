package dev.shortline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ClickEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);
    public static final String TOPIC = "shortline.clicks.v1";
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public ClickEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    @Async("clickPublisherExecutor")
    public void publish(String code, String referrer) {
        String domain = referrerDomain(referrer);
        var event = new ClickMessage(UUID.randomUUID().toString(), code, Instant.now(), domain);
        try {
            kafka.send(TOPIC, code, objectMapper.writeValueAsString(event))
                    .whenComplete((result, failure) -> {
                        if (failure != null) log.warn("Click event publish failed for code {}", code, failure);
                    });
        } catch (JsonProcessingException exception) {
            log.warn("Could not serialize click event for code {}", code, exception);
        }
    }

    private String referrerDomain(String referrer) {
        if (referrer == null || referrer.isBlank()) return null;
        try { return URI.create(referrer).getHost(); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public record ClickMessage(String eventId, String code, Instant occurredAt, String referrerDomain) {}
}
