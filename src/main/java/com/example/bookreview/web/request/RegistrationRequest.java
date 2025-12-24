package com.example.bookreview.web.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Use letters, numbers, and underscores only")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Please confirm your password")
        String confirmPassword,

        @Email(message = "Enter a valid email")
        String email
) {

    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        if (password == null) {
            return false;
        }
        return password.equals(confirmPassword);
    }

    public static RegistrationRequest empty() {
        return new RegistrationRequest("", "", "", "");
    }
}
