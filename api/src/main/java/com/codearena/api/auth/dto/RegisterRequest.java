package com.codearena.api.auth.dto;

import com.codearena.api.web.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "may contain only letters, digits and underscores")
        String username,

        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank @Size(min = 8) @MaxUtf8Bytes(72) String password
) {
}
