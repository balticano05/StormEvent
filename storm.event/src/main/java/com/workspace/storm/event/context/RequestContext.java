package com.workspace.storm.event.context;

import com.workspace.storm.event.config.TimeoutProperties;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record RequestContext(
        String requestId,
        Instant startedAt,
        Instant budgetDeadlineAt,
        Instant hardDeadlineAt,
        String lang,
        Long advisoryDeadlineMs
) {
    public static RequestContext create(String requestId, TimeoutProperties timeoutProps, String lang, Long advisoryDeadlineMs) {
        Objects.requireNonNull(timeoutProps, "timeoutProps must not be null");
        Instant now = Instant.now();
        long softBudget = timeoutProps.getSoftBudgetMs();
        long hardCeiling = timeoutProps.getHardCeilingMs();

        long effectiveBudget = Optional.ofNullable(advisoryDeadlineMs)
                .filter(ms -> ms > 0 && ms < softBudget)
                .orElse(softBudget);

        return new RequestContext(
                requestId,
                now,
                now.plusMillis(effectiveBudget),
                now.plusMillis(hardCeiling),
                lang,
                advisoryDeadlineMs
        );
    }

    public boolean isBudgetExceeded() {
        return Instant.now().isAfter(budgetDeadlineAt);
    }

    public boolean isHardDeadlineExceeded() {
        return Instant.now().isAfter(hardDeadlineAt);
    }

    public long remainingBudgetMs() {
        return Math.max(0, java.time.Duration.between(Instant.now(), budgetDeadlineAt).toMillis());
    }

    public long remainingHardMs() {
        return Math.max(0, java.time.Duration.between(Instant.now(), hardDeadlineAt).toMillis());
    }
}