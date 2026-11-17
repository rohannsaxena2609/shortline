package dev.shortline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "shortline_users")
public class ShortlineUser {
    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String username;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ShortlineUser() {}
    public ShortlineUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
}
