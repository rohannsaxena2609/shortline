package dev.shortline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShortLinkController {
    private final ShortLinkService service;

    public ShortLinkController(ShortLinkService service) { this.service = service; }

    @PostMapping("/api/v1/links")
    public ResponseEntity<CreatedLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        var created = service.create(request.url(), request.customCode(), request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedLinkResponse(
                created.code(), created.shortUrl(), created.destinationUrl(), created.expiresAt()));
    }

    @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        var result = service.resolve(code);
        if (result.isEmpty()) return ResponseEntity.notFound().build();
        var link = result.get();
        if (!link.active() || link.expired()) return ResponseEntity.status(HttpStatus.GONE).build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(link.destinationUrl()).toASCIIString()).build();
    }

    @DeleteMapping("/api/v1/links/{code}")
    public ResponseEntity<Void> deactivate(@PathVariable String code) {
        return service.deactivate(code) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ShortLinkService.InvalidLinkException.class)
    public ResponseEntity<ApiError> invalidLink(ShortLinkService.InvalidLinkException exception) {
        return ResponseEntity.badRequest().body(new ApiError("invalid_link", exception.getMessage()));
    }

    @ExceptionHandler(ShortLinkService.CodeAlreadyExistsException.class)
    public ResponseEntity<ApiError> duplicateCode(ShortLinkService.CodeAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("code_conflict", exception.getMessage()));
    }

    public record CreateLinkRequest(@NotBlank @Size(max = 2048) String url,
                                    @Size(max = 32) String customCode,
                                    Instant expiresAt) {}
    public record CreatedLinkResponse(String code, String shortUrl, String destinationUrl, Instant expiresAt) {}
    public record ApiError(String code, String message) {}
}
