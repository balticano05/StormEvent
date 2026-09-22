package com.workspace.storm.event;

import com.workspace.storm.event.client.BzdClient;
import com.workspace.storm.event.entity.bzd.BzdStation;
import com.workspace.storm.event.entity.bzd.BzdTrain;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class BzdClientIntegrationTest {

    @Autowired
    private BzdClient bzdClient;

    @Test
    void resolveStationsGrodno() {
        List<BzdStation> stations = bzdClient.resolveStations("Гродно");

        assertNotNull(stations);
        assertFalse(stations.isEmpty(), "Station list should not be empty for 'Гродно'");
        BzdStation s = stations.get(0);
        log.info("First match: value={}, exp={}, esr={}, gid={}", s.getValue(), s.getExp(), s.getEcp(), s.getGid());
        assertNotNull(s.getExp());
        assertNotNull(s.getEcp());
    }

    @Test
    void searchRouteGrodnoToBrest() {
        List<BzdTrain> trains = bzdClient.searchRoute("Гродно", "Брест", LocalDate.now().plusDays(1));

        assertNotNull(trains);
        assertFalse(trains.isEmpty(), "Train list should not be empty for Гродно -> Брест");
        log.info("Found {} trains, first: {} {} -> {}", trains.size(),
                trains.get(0).getNumber(), trains.get(0).getFromTime(), trains.get(0).getToTime());
    }

}