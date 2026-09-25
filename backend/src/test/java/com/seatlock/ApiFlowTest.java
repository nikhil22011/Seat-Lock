package com.seatlock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end over HTTP: register, create an event, hold, pay, check in, with real JWTs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void fullFlowOverHttp() throws Exception {
        String organizer = register("ORGANIZER");
        String user = register("USER");

        // Normal users can't create events.
        mvc.perform(post("/api/events").header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON).content(eventJson()))
                .andExpect(status().isForbidden());

        JsonNode event = body(mvc.perform(post("/api/events").header("Authorization", "Bearer " + organizer)
                        .contentType(MediaType.APPLICATION_JSON).content(eventJson()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long eventId = event.get("id").asLong();

        JsonNode seats = body(mvc.perform(get("/api/events/{id}/seats", eventId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long seatId = seats.get(0).get("id").asLong();

        // Holding requires login.
        mvc.perform(post("/api/events/{id}/holds", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seatIds\":[" + seatId + "]}"))
                .andExpect(status().isUnauthorized());

        JsonNode booking = body(mvc.perform(post("/api/events/{id}/holds", eventId)
                        .header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seatIds\":[" + seatId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andReturn().getResponse().getContentAsString());
        long bookingId = booking.get("id").asLong();

        // Missing Idempotency-Key is a client error.
        mvc.perform(post("/api/bookings/{id}/pay", bookingId).header("Authorization", "Bearer " + user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        JsonNode paid = body(mvc.perform(post("/api/bookings/{id}/pay", bookingId)
                        .header("Authorization", "Bearer " + user)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("SUCCEEDED"))
                .andReturn().getResponse().getContentAsString());
        String ticket = paid.get("booking").get("ticketCode").asText();

        mvc.perform(post("/api/checkin").header("Authorization", "Bearer " + organizer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + ticket + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[0]").value("A1"));

        mvc.perform(get("/api/organizer/events").header("Authorization", "Bearer " + organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookedSeats").value(1))
                .andExpect(jsonPath("$[0].checkedIn").value(1));
    }

    private String register(String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@test.dev";
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test","email":"%s","password":"password123","role":"%s"}
                                """.formatted(email, role)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return body(res).get("token").asText();
    }

    private static String eventJson() {
        return """
                {"title":"HTTP Test Show","venue":"Hall","city":"Pune","startsAt":"2099-01-01T18:00:00Z",
                 "rows":[{"label":"A","seatCount":3,"category":"Regular","priceCents":50000}]}
                """;
    }

    private JsonNode body(String s) throws Exception {
        return json.readTree(s);
    }
}
