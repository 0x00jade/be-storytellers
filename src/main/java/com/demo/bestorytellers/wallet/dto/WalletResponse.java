package com.demo.bestorytellers.wallet.dto;

import java.math.BigDecimal;
import java.util.List;

public record WalletResponse(
    BigDecimal balance,
    String currency,
    List<TransactionResponse> recentTransactions
) {}
