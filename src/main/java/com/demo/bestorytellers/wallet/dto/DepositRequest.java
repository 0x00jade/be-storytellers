package com.demo.bestorytellers.wallet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DepositRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Minimum deposit is 0.01")
    @DecimalMax(value = "1000.00", message = "Maximum deposit per transaction is 1000.00")
    BigDecimal amount,

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 64, message = "Idempotency key must be at most 64 characters")
    String idempotencyKey,

    @NotBlank(message = "Payment method token is required")
    String paymentMethodToken
) {}
