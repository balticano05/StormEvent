package com.workspace.storm.event;

import com.workspace.storm.event.entity.Event;
import com.workspace.storm.event.service.TicketProService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TicketProServiceIntegrationTest {

    @Autowired
    private TicketProService ticketProService;

    @Test
    void collectHockeyEvents() {
        List<Event> events = ticketProService.collectEvents(
                "Sport / Hockey",
                "/bilety-na-sportivnye-meropriyatiya/bilety-na-xokkej/");

        assertNotNull(events);
        assertFalse(events.isEmpty(), "Hockey events list should not be empty");

        long named = events.stream()
                .filter(e -> e.getName() != null && !e.getName().isBlank())
                .count();
        log.info("Hockey events collected: {} ({} with name)", events.size(), named);

        assertFalse(named == 0, "At least one event must have a name");
    }

}