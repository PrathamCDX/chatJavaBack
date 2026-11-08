package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record FileInitiateRequest(
        @NotBlank @Pattern(regexp = "\\.?[A-Za-z0-9]{1,10}", message = "must be letters and digits, e.g. jpg") String extension,
        @NotNull @Positive Long sizeKb,
        @NotNull UUID messageId,
        @NotNull @Positive Long parts) {
}
