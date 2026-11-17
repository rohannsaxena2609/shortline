package dev.shortline;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortLinkService {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 7;
    private static final int MAX_ATTEMPTS = 5;
    private static final Pattern VALID_CODE = Pattern.compile("[A-Za-z0-9_-]{3,32}");
    private final SecureRandom random = new SecureRandom();
    private final ShortLinkRepository repository;
    private final LinkCache cache;
    private final String baseUrl;

    public ShortLinkService(ShortLinkRepository repository, LinkCache cache,
                            @org.springframework.beans.factory.annotation.Value("${shortline.base-url}") String baseUrl) {
        this.repository = repository;
        this.cache = cache;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public CreatedLink create(String rawUrl, String customCode, Instant expiresAt) {
        String destination = validateAndNormalize(rawUrl);
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new InvalidLinkException("Expiration must be in the future");
        }
        if (customCode != null) {
            if (!VALID_CODE.matcher(customCode).matches()) {
                throw new InvalidLinkException("Custom codes must be 3–32 letters, numbers, hyphens, or underscores");
            }
            if (repository.existsById(customCode)) throw new CodeAlreadyExistsException(customCode);
            return persist(customCode, destination, expiresAt);
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            try {
                return persist(code, destination, expiresAt);
            } catch (DataIntegrityViolationException collision) {
                if (repository.existsById(code)) continue;
                throw collision;
            }
        }
        throw new IllegalStateException("Could not allocate a unique short code; retry the request");
    }

    @Transactional
    public CreatedLink persist(String code, String destination, Instant expiresAt) {
        repository.saveAndFlush(new ShortLink(code, destination, expiresAt));
        return new CreatedLink(code, baseUrl + "/" + code, destination, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedLink> resolve(String code) {
        Optional<String> cached = cache.get(code);
        if (cached.isPresent()) return Optional.of(new ResolvedLink(cached.get(), true, false, null));
        return repository.findById(code).map(link -> {
            boolean expired = link.isExpired(Instant.now());
            if (link.isActive() && !expired) cache.put(code, link.getDestinationUrl(), link.getExpiresAt());
            return new ResolvedLink(link.getDestinationUrl(), link.isActive(), expired, link.getExpiresAt());
        });
    }

    @Transactional
    public boolean deactivate(String code) {
        return repository.findById(code).map(link -> {
            link.deactivate();
            repository.save(link);
            cache.evict(code);
            return true;
        }).orElse(false);
    }

    private String validateAndNormalize(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim()).normalize();
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new InvalidLinkException("Only absolute HTTP and HTTPS URLs are supported");
            }
            String normalized = uri.toASCIIString();
            if (normalized.length() > 2048) throw new InvalidLinkException("URL must be 2048 characters or fewer");
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new InvalidLinkException("Provide a valid absolute HTTP or HTTPS URL");
        }
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }

    public record CreatedLink(String code, String shortUrl, String destinationUrl, Instant expiresAt) {}
    public record ResolvedLink(String destinationUrl, boolean active, boolean expired, Instant expiresAt) {}

    public static class InvalidLinkException extends RuntimeException {
        public InvalidLinkException(String message) { super(message); }
    }
    public static class CodeAlreadyExistsException extends RuntimeException {
        public CodeAlreadyExistsException(String code) { super("Short code is already in use: " + code); }
    }
}
