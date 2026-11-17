package dev.shortline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShortLinkController {
    private final ShortLinkService service;

    public ShortLinkController(ShortLinkService service) { this.service = service; }

    @PostMapping("/api/v1/links")
    public ResponseEntity<CreatedLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        var created = service.create(request.url());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedLinkResponse(created.code(), created.shortUrl(), created.destinationUrl()));
    }

    @GetMapping("/{code:[A-Za-z0-9]{7}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        return service.resolve(code)
                .map(url -> ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, URI.create(url).toASCIIString()).<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(ShortLinkService.InvalidDestinationException.class)
    public ResponseEntity<ApiError> invalidDestination(ShortLinkService.InvalidDestinationException exception) {
        return ResponseEntity.badRequest().body(new ApiError("invalid_url", exception.getMessage()));
    }

    public record CreateLinkRequest(@NotBlank @Size(max = 2048) String url) {}
    public record CreatedLinkResponse(String code, String shortUrl, String destinationUrl) {}
    public record ApiError(String code, String message) {}
}
