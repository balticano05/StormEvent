package com.workspace.storm.event.gateway;

import java.time.LocalDate;

public record TransportQuery(String from, String to, LocalDate date, int passengers,
                             String window, boolean directOnly, boolean roundTrip) {

    public TransportQuery {
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("from must not be blank");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("to must not be blank");
        }
        if (date == null) {
            date = LocalDate.now().plusDays(1);
        }
        if (passengers < 1) {
            passengers = 1;
        }
    }
}
