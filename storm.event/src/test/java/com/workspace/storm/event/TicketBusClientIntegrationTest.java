package com.workspace.storm.event;

import com.workspace.storm.event.client.TicketBusClient;
import com.workspace.storm.event.dto.TicketBusSearchRequest;
import com.workspace.storm.event.entity.tb.TbRace;
import com.workspace.storm.event.entity.tb.TbSchedule;
import com.workspace.storm.event.entity.tb.TbStation;
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
public class TicketBusClientIntegrationTest {

    private static final String ORIGIN_MINSK = "500000";
    private static final String DEST_GRODNO = "400001";

    @Autowired
    private TicketBusClient ticketBusClient;

    @Test
    void resolveStationsShchuchinFromMinsk() {
        List<TbStation> stations = ticketBusClient.resolveStations("ЩУЧИН", ORIGIN_MINSK, true);

        assertNotNull(stations);
        assertFalse(stations.isEmpty(), "Station list should not be empty for 'ЩУЧИН'");
        TbStation s = stations.get(0);
        log.info("First match: id={}, name={}, desc={}", s.getId(), s.getName(), s.getDescription());
        assertNotNull(s.getId());
        assertNotNull(s.getName());
    }

    @Test
    void searchRacesMinskToGrodno() {
        TicketBusSearchRequest req = new TicketBusSearchRequest();
        req.setFromId(ORIGIN_MINSK);
        req.setToId(DEST_GRODNO);
        req.setDate(LocalDate.now().plusDays(1));

        List<TbRace> races = ticketBusClient.searchRaces(req);

        assertNotNull(races);
        assertFalse(races.isEmpty(), "Races should not be empty for МИНСК -> ГРОДНО АВ");
        TbRace race = races.get(0);
        log.info("First race: code={}, route={}, dep={} -> arr={}, price={}",
                race.getCode(), race.getRoute(), race.getDeparture(), race.getArrival(), race.getPrice());
        assertNotNull(race.getCode());
        assertNotNull(race.getRoute());
        assertNotNull(race.getDeparture());
    }

    @Test
    void routeScheduleMinskToGrodno() {
        TicketBusSearchRequest req = new TicketBusSearchRequest();
        req.setFromId(ORIGIN_MINSK);
        req.setToId(DEST_GRODNO);
        req.setDate(LocalDate.now().plusDays(1));

        List<TbSchedule> schedules = ticketBusClient.routeSchedule(req);

        assertNotNull(schedules);
        assertFalse(schedules.isEmpty(), "Schedule should not be empty for МИНСК -> ГРОДНО АВ");
        TbSchedule schedule = schedules.get(0);
        log.info("First schedule: code={}, route={}, forward={}, availability={}",
                schedule.getCode(), schedule.getRoute(), schedule.getForward(), schedule.getAvailability());
        assertNotNull(schedule.getCode());
        assertNotNull(schedule.getRoute());
    }

    @Test
    void raceStopsForBrestRace() {
        TicketBusSearchRequest req = new TicketBusSearchRequest();
        req.setFromId(ORIGIN_MINSK);
        req.setToId("100001");
        req.setDate(LocalDate.now().plusDays(1));

        List<TbRace> races = ticketBusClient.searchRaces(req);
        assertFalse(races.isEmpty(), "Races should not be empty for МИНСК -> БРЕСТ АВ");

        List<com.workspace.storm.event.entity.tb.TbStop> stops =
                ticketBusClient.raceStops(races.get(0).getCode());

        assertNotNull(stops);
        log.info("Race {} has {} stops", races.get(0).getCode(), stops.size());
    }

}