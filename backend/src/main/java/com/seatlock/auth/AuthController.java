package com.seatlock.auth;

import com.seatlock.auth.AuthDtos.AuthResponse;
import com.seatlock.auth.AuthDtos.LoginRequest;
import com.seatlock.auth.AuthDtos.RegisterRequest;
import com.seatlock.auth.AuthDtos.UserView;
import com.seatlock.common.ApiException;
import com.seatlock.user.Role;
import com.seatlock.user.User;
import com.seatlock.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        Role role = req.role() == null ? Role.USER : req.role();
        User user = users.save(new User(req.name().trim(), email, passwordEncoder.encode(req.password()), role));
        return new AuthResponse(tokens.issue(user), UserView.of(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));
        return new AuthResponse(tokens.issue(user), UserView.of(user));
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(Long.valueOf(jwt.getSubject()))
                .map(UserView::of)
                .orElseThrow(() -> ApiException.notFound("User"));
    }
}
