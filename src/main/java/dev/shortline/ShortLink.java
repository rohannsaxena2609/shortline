package dev.shortline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "short_links")
public class ShortLink {
    @Id
    @Column(length = 32, nullable = false, updatable = false)
    private String code;

    @Column(name = "destination_url", nullable = false, length = 2048)
    private String destinationUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "owner_id", length = 64)
    private String ownerId;

    @Column(nullable = false)
    private boolean active;

    protected ShortLink() {}

    public ShortLink(String code, String destinationUrl, Instant expiresAt, String ownerId) {
        this.code = code;
        this.destinationUrl = destinationUrl;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.ownerId = ownerId;
        this.active = true;
    }

    public String getCode() { return code; }
    public String getDestinationUrl() { return destinationUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getOwnerId() { return ownerId; }
    public boolean isActive() { return active; }
    public boolean isExpired(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }
    public void deactivate() { this.active = false; }
}
