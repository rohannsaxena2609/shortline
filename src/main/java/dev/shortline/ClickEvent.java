package dev.shortline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "click_events")
public class ClickEvent {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "referrer_domain", length = 255)
    private String referrerDomain;

    protected ClickEvent() {}

    public ClickEvent(String eventId, String code, Instant occurredAt, String referrerDomain) {
        this.eventId = eventId;
        this.code = code;
        this.occurredAt = occurredAt;
        this.referrerDomain = referrerDomain;
    }

    public String getCode() { return code; }
}
