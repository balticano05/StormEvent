package com.workspace.storm.event.db.maintenance;

import com.workspace.storm.event.config.DbProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Фоновое обслуживание базы: партиции и ретеншн (шаги 311, 352-353).
 *
 * <p>Раньше обе задачи существовали только как классы и вызывались тестами:
 * в работающем приложении месячные партиции не создавались, а журналы не
 * убирались. Через {@code partition-months-ahead} месяцев запись в ещё не
 * существующий месяц ушла бы в {@code _default} и осталась там навсегда, а
 * журналы росли бы без ограничения.
 *
 * <p>Расписание: создание партиций — раз в сутки (месяц не наступит за ночь),
 * полный проход уборки — раз в час ночью и раз в сутки днём. Ночной проход
 * идёт целиком, дневной — только истёкшее: журналы большими порциями чистить
 * днём незачем, а сессии и кэш это нужно часто.
 *
 * <p>Ошибки внутри задачи не поднимаются наружу: упавший проход уборки не
 * должен уронить приложение или отключить инстанс от трафика.
 *
 * <p>В тестах обслуживание выключено через {@code storm.db.maintenance-enabled=false}:
 * контейнер Postgres общий на весь прогон, и партиции, созданные фоновой задачей,
 * меняют число таблиц и планы запросов у соседних тестов. Сами {@code PartitionMaintenance}
 * и {@code RetentionService} тестами вызываются напрямую.
 */
@Component
@RequiredArgsConstructor
public class DbMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(DbMaintenanceScheduler.class);

    private final PartitionMaintenance partitions;
    private final RetentionService retention;
    private final DbProperties properties;

    /**
     * На старте партиции создаются сразу, а не по расписанию: до первого
     * прохода в сутки новый месяц может уже начаться, и первые записи ушли бы
     * в {@code _default}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!properties.isMaintenanceEnabled()) {
            log.debug("Фоновое обслуживание БД выключено");
            return;
        }
        try {
            List<String> created = partitions.ensureUpcoming();
            List<String> dropped = partitions.dropOlderThanRetention();
            log.info("Обслуживание БД при старте: создано партиций {}, удалено {}", created.size(), dropped.size());
        } catch (RuntimeException e) {
            log.error("Обслуживание партиций при старте не удалось: {}", e.toString());
        }
    }

    /** Создаёт партиции на месяцы вперёд и убирает месяцы за ретеншном. */
    @Scheduled(cron = "${storm.db.maintenance.partition-cron:0 10 3 * * *}")
    public void maintainPartitions() {
        if (!properties.isMaintenanceEnabled()) {
            return;
        }
        try {
            List<String> created = partitions.ensureUpcoming();
            List<String> dropped = partitions.dropOlderThanRetention();
            if (!created.isEmpty() || !dropped.isEmpty()) {
                log.info("Партиции: создано {}, удалено {}", created.size(), dropped.size());
            }
        } catch (RuntimeException e) {
            log.error("Создание или удаление партиций не удалось: {}", e.toString());
        }
    }

    /** Ночной проход: истёкшее плюс добор журналов. */
    @Scheduled(cron = "${storm.db.maintenance.nightly-cron:0 0 4 * * *}")
    public void nightlySweep() {
        sweep(true);
    }

    /** Дневной проход: только истёкшее, журналы трогать незачем. */
    @Scheduled(cron = "${storm.db.maintenance.hourly-cron:0 0 * * * *}")
    public void hourlySweep() {
        sweep(false);
    }

    private void sweep(boolean withLogs) {
        if (!properties.isMaintenanceEnabled()) {
            return;
        }
        try {
            Instant now = Instant.now();
            RetentionService.SweepResult expired = retention.cleanExpired(now);
            RetentionService.SweepResult logs = withLogs
                    ? retention.trimLogs(now)
                    : new RetentionService.SweepResult(0, 0, 0, 0, 0);
            RetentionService.SweepResult total = expired.plus(logs);
            if (total.total() > 0) {
                log.info("Уборка БД: сессии {}, дедупликация {}, кэш {}, журнал запросов {}, журнал ошибок {}",
                        total.sessions(), total.idempotency(), total.cache(),
                        total.requestLog(), total.sourceErrors());
            }
        } catch (RuntimeException e) {
            log.error("Проход уборки БД не удался: {}", e.toString());
        }
    }

    }