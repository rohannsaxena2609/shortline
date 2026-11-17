package dev.shortline;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickAnalyticsConsumerTest {
    @Mock ClickEventRepository events;

    @Test
    void duplicateDeliveryDoesNotPersistTheEventTwice() throws Exception {
        var consumer = new ClickAnalyticsConsumer(events, new ObjectMapper().findAndRegisterModules());
        String message = "{\"eventId\":\"event-1\",\"code\":\"docs\",\"occurredAt\":\"2026-11-17T00:00:00Z\",\"referrerDomain\":\"news.example\"}";
        when(events.existsById("event-1")).thenReturn(false, true);
        consumer.consume(message);
        consumer.consume(message);
        verify(events, times(1)).saveAndFlush(any(ClickEvent.class));
    }
}
