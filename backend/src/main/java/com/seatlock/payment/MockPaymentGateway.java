package com.seatlock.payment;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Test-mode gateway: always approves unless the demo asks it to decline. */
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(long bookingId, int amountCents, String idempotencyKey, String simulate) {
        if ("FAIL".equalsIgnoreCase(simulate)) {
            return PaymentResult.failure("Card declined by issuing bank (simulated)");
        }
        return PaymentResult.success("mock_pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    }
}
