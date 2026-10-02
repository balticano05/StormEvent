package com.workspace.storm.event.scheduler;

import com.workspace.storm.event.client.BzdClient;
import com.workspace.storm.event.service.TicketBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Warmup для BZD и TicketBus: пробный запрос каждые 5 минут, ошибки не роняют приложение. */
@Component
public class WarmupScheduler {

    private static final Logger log = LoggerFactory.getLogger(WarmupScheduler.class);

    private final BzdClient bzdClient;
    private final TicketBusService ticketBusService;

    public WarmupScheduler(BzdClient bzdClient, TicketBusService ticketBusService) {
        this.bzdClient = bzdClient;
        this.ticketBusService = ticketBusService;
    }

    @Scheduled(initialDelayString = "${storm.warmup.initial-ms:0}", fixedDelayString = "${storm.warmup.period-ms:300000}")
    public void warmup() {
        try {
            bzdClient.resolveStations("минск");
        } catch (Exception e) {
            log.warn("BZD warmup failed: {}", e.getMessage());
        }
        try {
            ticketBusService.findStations("минск", null, true);
        } catch (Exception e) {
            log.warn("TicketBus warmup failed: {}", e.getMessage());
        }
    }
}
