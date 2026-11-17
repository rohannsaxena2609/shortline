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
    @Column(length = 16, nullable = false, updatable = false)
    private String code;

    @Column(name = "destination_url", nullable = false, length = 2048)
    private String destinationUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ShortLink() {}

    public ShortLink(String code, String destinationUrl) {
        this.code = code;
        this.destinationUrl = destinationUrl;
        this.createdAt = Instant.now();
    }

    public String getCode() { return code; }
    public String getDestinationUrl() { return destinationUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
