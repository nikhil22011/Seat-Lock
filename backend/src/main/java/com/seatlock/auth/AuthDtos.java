package com.seatlock.auth;

import com.seatlock.user.Role;
import com.seatlock.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            /** USER (default) or ORGANIZER. */
            Role role
    ) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserView(Long id, String name, String email, Role role) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRole());
        }
    }

    public record AuthResponse(String token, UserView user) {}
}
