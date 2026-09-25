package com.seatlock.payment;

public record PaymentResult(PaymentStatus status, String providerRef, String failureReason) {

    public static PaymentResult success(String providerRef) {
        return new PaymentResult(PaymentStatus.SUCCEEDED, providerRef, null);
    }

    public static PaymentResult failure(String reason) {
        return new PaymentResult(PaymentStatus.FAILED, null, reason);
    }

    public boolean succeeded() {
        return status == PaymentStatus.SUCCEEDED;
    }
}
