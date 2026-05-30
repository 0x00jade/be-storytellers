package com.demo.bestorytellers.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyGoogleToken(@NotBlank String accessToken) {}