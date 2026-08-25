package com.demo.bestorytellers.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    String type,
    String status,
    BigDecimal amount,
    String description,
    Instant createdAt
) {}
