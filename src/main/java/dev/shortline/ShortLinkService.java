package dev.shortline;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortLinkService {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 7;
    private static final int MAX_ATTEMPTS = 5;
    private final SecureRandom random = new SecureRandom();
    private final ShortLinkRepository repository;
    private final String baseUrl;

    public ShortLinkService(ShortLinkRepository repository,
                            @org.springframework.beans.factory.annotation.Value("${shortline.base-url}") String baseUrl) {
        this.repository = repository;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public CreatedLink create(String rawUrl) {
        String destination = validateAndNormalize(rawUrl);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            try {
                repository.saveAndFlush(new ShortLink(code, destination));
                return new CreatedLink(code, baseUrl + "/" + code, destination);
            } catch (DataIntegrityViolationException collision) {
                if (repository.existsById(code)) continue;
                throw collision;
            }
        }
        throw new IllegalStateException("Could not allocate a unique short code; retry the request");
    }

    @Transactional(readOnly = true)
    public Optional<String> resolve(String code) {
        return repository.findById(code).map(ShortLink::getDestinationUrl);
    }

    private String validateAndNormalize(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim()).normalize();
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new InvalidDestinationException("Only absolute HTTP and HTTPS URLs are supported");
            }
            String normalized = uri.toASCIIString();
            if (normalized.length() > 2048) {
                throw new InvalidDestinationException("URL must be 2048 characters or fewer");
            }
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new InvalidDestinationException("Provide a valid absolute HTTP or HTTPS URL");
        }
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }

    public record CreatedLink(String code, String shortUrl, String destinationUrl) {}

    public static class InvalidDestinationException extends RuntimeException {
        public InvalidDestinationException(String message) { super(message); }
    }
}
