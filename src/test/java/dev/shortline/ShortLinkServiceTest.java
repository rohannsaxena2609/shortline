package dev.shortline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTest {
    @Mock ShortLinkRepository repository;
    @Mock LinkCache cache;
    private ShortLinkService service;

    @BeforeEach
    void setUp() { service = new ShortLinkService(repository, cache, "https://s.example"); }

    @Test
    void createsGeneratedCodeForValidHttpUrl() {
        when(repository.saveAndFlush(any(ShortLink.class))).thenAnswer(call -> call.getArgument(0));
        var created = service.create("https://example.com/a", null, null, "alice");
        assertEquals(7, created.code().length());
        assertEquals("https://s.example/" + created.code(), created.shortUrl());
        assertEquals("https://example.com/a", created.destinationUrl());
    }

    @Test
    void acceptsCustomCodeAndExpiration() {
        when(repository.existsById("docs")).thenReturn(false);
        when(repository.saveAndFlush(any(ShortLink.class))).thenAnswer(call -> call.getArgument(0));
        var expiry = Instant.now().plusSeconds(3600);
        var created = service.create("https://example.com", "docs", expiry, "alice");
        assertEquals("docs", created.code());
        assertEquals(expiry, created.expiresAt());
    }

    @Test
    void rejectsUnsafeAndMalformedUrls() {
        assertThrows(ShortLinkService.InvalidLinkException.class,
                () -> service.create("javascript:alert(1)", null, null, "alice"));
        assertThrows(ShortLinkService.InvalidLinkException.class,
                () -> service.create("not a url", null, null, "alice"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsExpiredDatesAndInvalidAliases() {
        assertThrows(ShortLinkService.InvalidLinkException.class,
                () -> service.create("https://example.com", null, Instant.now().minusSeconds(1), "alice"));
        assertThrows(ShortLinkService.InvalidLinkException.class,
                () -> service.create("https://example.com", "bad alias", null, "alice"));
    }

    @Test
    void rejectsDuplicateCustomCode() {
        when(repository.existsById("docs")).thenReturn(true);
        assertThrows(ShortLinkService.CodeAlreadyExistsException.class,
                () -> service.create("https://example.com", "docs", null, "alice"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void retriesGeneratedCodeCollision() {
        when(repository.saveAndFlush(any(ShortLink.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenAnswer(call -> call.getArgument(0));
        when(repository.existsById(anyString())).thenReturn(true, false);
        var created = service.create("https://example.com", null, null, "alice");
        assertEquals(7, created.code().length());
        verify(repository, times(2)).saveAndFlush(any(ShortLink.class));
    }

    @Test
    void onlyOwnerCanDeactivateAndCacheIsEvicted() {
        var link = new ShortLink("docs", "https://example.com", null, "alice");
        when(repository.findById("docs")).thenReturn(Optional.of(link));
        assertFalse(service.deactivate("docs", "bob"));
        assertTrue(service.deactivate("docs", "alice"));
        verify(cache).evict("docs");
    }

    @Test
    void resolutionMarksExpiredLinksAndDoesNotCacheThem() {
        var expired = new ShortLink("docs", "https://example.com", Instant.now().minusSeconds(10), "alice");
        when(cache.get("docs")).thenReturn(Optional.empty());
        when(repository.findById("docs")).thenReturn(Optional.of(expired));
        var result = service.resolve("docs").orElseThrow();
        assertTrue(result.expired());
        verify(cache, never()).put(anyString(), anyString(), any());
    }

    @Test
    void resolutionUsesCacheAndFallsBackToDatabase() {
        when(cache.get("docs")).thenReturn(Optional.of("https://example.com"));
        var cached = service.resolve("docs").orElseThrow();
        assertTrue(cached.active());
        verify(repository, never()).findById("docs");

        when(cache.get("miss")).thenReturn(Optional.empty());
        when(repository.findById("miss")).thenReturn(Optional.empty());
        assertTrue(service.resolve("miss").isEmpty());
    }
}
