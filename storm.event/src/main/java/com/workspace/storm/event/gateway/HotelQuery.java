package com.workspace.storm.event.gateway;

import java.time.LocalDate;

public record HotelQuery(String city, LocalDate checkIn, LocalDate checkOut, int adults) {

    public HotelQuery {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("city must not be blank");
        }
        if (checkIn == null) {
            checkIn = LocalDate.now().plusDays(1);
        }
        if (checkOut == null) {
            checkOut = checkIn.plusDays(1);
        }
        if (adults < 1) {
            adults = 1;
        }
    }
}
