package com.workspace.storm.event.scheduler;

import com.workspace.storm.event.client.BzdClient;
import com.workspace.storm.event.service.TicketBusService;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WarmupSchedulerTest {

    @Test
    void warmupNeverThrows() {
        WarmupScheduler scheduler = new WarmupScheduler(new BzdClient(new okhttp3.OkHttpClient(), null, null), new TicketBusService((com.workspace.storm.event.client.TicketBusClient) null));
        assertDoesNotThrow(scheduler::warmup);
    }
}
