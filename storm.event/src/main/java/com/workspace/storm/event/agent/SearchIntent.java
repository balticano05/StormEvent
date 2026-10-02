package com.workspace.storm.event.agent;

import java.time.LocalDate;
import java.util.List;

public record SearchIntent(List<String> domains, String from, String to, LocalDate date, int passengers,
                           boolean roundTrip, String budget) {

    public SearchIntent {
        domains = domains == null ? List.of() : List.copyOf(domains);
        if (passengers < 1) {
            passengers = 1;
        }
    }
}
