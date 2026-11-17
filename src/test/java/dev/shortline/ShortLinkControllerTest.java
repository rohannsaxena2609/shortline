package dev.shortline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ShortLinkControllerTest {
    @Mock ShortLinkService links;
    @Mock ClickEventPublisher publisher;
    @Mock ClickEventRepository events;
    private ShortLinkController controller;

    @BeforeEach
    void setUp() { controller = new ShortLinkController(links, publisher, events, new SimpleMeterRegistry()); }

    @Test
    void redirectsActiveLinkAndQueuesAnalytics() {
        when(links.resolve("docs")).thenReturn(Optional.of(
                new ShortLinkService.ResolvedLink("https://example.com/docs", true, false, null)));
        var response = controller.redirect("docs", "https://news.example/article");
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://example.com/docs", response.getHeaders().getLocation().toString());
        verify(publisher).publish("docs", "https://news.example/article");
    }

    @Test
    void returnsNotFoundForUnknownLink() {
        when(links.resolve("missing")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, controller.redirect("missing", null).getStatusCode());
        verifyNoInteractions(publisher);
    }

    @Test
    void returnsGoneForExpiredLinkWithoutPublishingClick() {
        when(links.resolve("docs")).thenReturn(Optional.of(
                new ShortLinkService.ResolvedLink("https://example.com/docs", true, true, Instant.now().minusSeconds(1))));
        assertEquals(HttpStatus.GONE, controller.redirect("docs", null).getStatusCode());
        verifyNoInteractions(publisher);
    }

    @Test
    void returnsGoneForDeactivatedLinkWithoutPublishingClick() {
        when(links.resolve("docs")).thenReturn(Optional.of(
                new ShortLinkService.ResolvedLink("https://example.com/docs", false, false, null)));
        assertEquals(HttpStatus.GONE, controller.redirect("docs", null).getStatusCode());
        verifyNoInteractions(publisher);
    }
}
