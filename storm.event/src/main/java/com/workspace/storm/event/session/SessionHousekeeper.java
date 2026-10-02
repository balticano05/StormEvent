package com.workspace.storm.event.session;

import com.workspace.storm.event.db.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SessionHousekeeper {

    private static final Logger log = LoggerFactory.getLogger(SessionHousekeeper.class);

    private final SessionRepository sessions;

    public SessionHousekeeper(SessionRepository sessions) {
        this.sessions = sessions;
    }

    @Scheduled(fixedDelayString = "${storm.session.housekeeper-ms:60000}")
    public void cleanup() {
        try {
            int removed = sessions.deleteExpired(Instant.now(), 1000);
            if (removed > 0) {
                log.info("SessionHousekeeper: удалено {} истёкших сессий", removed);
            }
        } catch (Exception e) {
            log.warn("SessionHousekeeper failed", e);
        }
    }
}
