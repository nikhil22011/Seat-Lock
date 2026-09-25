package com.seatlock.ticket;

import com.seatlock.config.SeatLockProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Ticket codes look like "SL1.42.Xk3...". The last part is an HMAC signature of the booking id,
 * so a forged or edited QR code is rejected before we even touch the database.
 */
@Component
public class TicketCodec {

    private static final String PREFIX = "SL1";
    private static final int SIGNATURE_BYTES = 16;

    private final byte[] secret;

    public TicketCodec(SeatLockProperties props) {
        this.secret = props.ticketSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String encode(long bookingId) {
        return PREFIX + "." + bookingId + "." + base64(sign(bookingId));
    }

    /** Returns the booking id if the signature is genuine. */
    public Optional<Long> decode(String code) {
        if (code == null) return Optional.empty();
        String[] parts = code.trim().split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) return Optional.empty();
        try {
            long id = Long.parseLong(parts[1]);
            byte[] given = Base64.getUrlDecoder().decode(parts[2]);
            // Constant-time compare so the signature can't be guessed byte by byte from timing.
            return MessageDigest.isEqual(given, sign(id)) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(long bookingId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] full = mac.doFinal((PREFIX + ":" + bookingId).getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(full, SIGNATURE_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
