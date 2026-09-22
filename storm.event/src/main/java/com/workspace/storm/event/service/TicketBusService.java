package com.workspace.storm.event.service;

import com.workspace.storm.event.client.TicketBusClient;
import com.workspace.storm.event.dto.TicketBusSearchRequest;
import com.workspace.storm.event.entity.tb.TbRace;
import com.workspace.storm.event.entity.tb.TbSchedule;
import com.workspace.storm.event.entity.tb.TbStation;
import com.workspace.storm.event.entity.tb.TbStop;
import com.workspace.storm.event.exception.TicketBusServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TicketBusService {

    private final TicketBusClient client;

    public List<TbStation> findStations(String query, String originId, boolean fromCity) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }
        return client.resolveStations(query, originId, fromCity);
    }

    public List<TbRace> searchRaces(TicketBusSearchRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("req must not be null");
        }
        try {
            return client.searchRaces(req);
        } catch (RuntimeException e) {
            throw new TicketBusServiceException("Failed to search TicketBus races", e);
        }
    }

    public List<TbSchedule> routeSchedule(TicketBusSearchRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("req must not be null");
        }
        try {
            return client.routeSchedule(req);
        } catch (RuntimeException e) {
            throw new TicketBusServiceException("Failed to load TicketBus schedule", e);
        }
    }

    public List<TbStop> raceStops(String raceCode) {
        if (raceCode == null || raceCode.isBlank()) {
            throw new IllegalArgumentException("raceCode must not be null or blank");
        }
        return client.raceStops(raceCode);
    }

}