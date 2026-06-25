package com.demo.bestorytellers.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchaseResponse(
    UUID chapterId,
    int chapterNumber,
    BigDecimal amountCharged,
    BigDecimal newBalance,
    Instant purchasedAt
) {}
