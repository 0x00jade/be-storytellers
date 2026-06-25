package com.demo.bestorytellers.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.payment.mock", havingValue = "true", matchIfMissing = true)
public class MockPaymentGatewayService implements PaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGatewayService.class);

    @Override
    public String charge(String paymentMethodToken, BigDecimal amount) {
        String chargeId = "mock_charge_" + UUID.randomUUID();
        log.info("Mock payment: charged {} for token={} → chargeId={}", amount, paymentMethodToken, chargeId);
        return chargeId;
    }
}
