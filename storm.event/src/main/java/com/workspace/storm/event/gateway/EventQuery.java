package com.workspace.storm.event.gateway;

import java.time.LocalDate;

public record EventQuery(String city, LocalDate date, String category) {

    public EventQuery {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("city must not be blank");
        }
        if (date == null) {
            date = LocalDate.now().plusDays(1);
        }
    }
}
