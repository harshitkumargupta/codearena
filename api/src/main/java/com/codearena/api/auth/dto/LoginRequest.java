package com.codearena.api.auth.dto;

import com.codearena.api.web.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @MaxUtf8Bytes(72) String password) {
}
