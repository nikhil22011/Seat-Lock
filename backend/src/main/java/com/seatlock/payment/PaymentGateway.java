package com.seatlock.payment;

/**
 * Boundary to the payment provider. The app ships with {@link MockPaymentGateway};
 * a Razorpay or Stripe implementation can replace it without touching booking logic.
 */
public interface PaymentGateway {

    /**
     * @param idempotencyKey forwarded to the provider so it also refuses to charge twice
     * @param simulate       demo-only hint ("FAIL" forces a decline); real gateways ignore it
     */
    PaymentResult charge(long bookingId, int amountCents, String idempotencyKey, String simulate);
}
