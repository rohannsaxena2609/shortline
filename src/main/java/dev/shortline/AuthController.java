package dev.shortline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final ShortlineUserRepository users;
    private final PasswordEncoder passwords;

    public AuthController(ShortlineUserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisteredUser> register(@Valid @RequestBody Registration request) {
        if (users.existsById(request.username())) throw new UsernameConflictException();
        users.saveAndFlush(new ShortlineUser(request.username(), passwords.encode(request.password())));
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisteredUser(request.username()));
    }

    @ExceptionHandler(UsernameConflictException.class)
    ResponseEntity<ApiError> usernameConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("username_taken", "Username is already registered"));
    }

    public record Registration(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String username,
                               @NotBlank @Size(min = 12, max = 72) String password) {}
    public record RegisteredUser(String username) {}
    public record ApiError(String code, String message) {}
    static class UsernameConflictException extends RuntimeException {}
}
