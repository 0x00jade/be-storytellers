package com.demo.bestorytellers.wallet.service;

import java.math.BigDecimal;

public interface PaymentGatewayService {
    /**
     * Charges the given payment method and returns an external charge ID.
     * Throws ValidationException if the charge is declined.
     */
    String charge(String paymentMethodToken, BigDecimal amount);
}
