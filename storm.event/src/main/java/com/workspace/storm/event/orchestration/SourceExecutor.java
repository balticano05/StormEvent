package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.context.RequestContext;
import com.workspace.storm.event.dto.source.PrincipalResult;

import java.util.ArrayList;
import java.util.List;

public class SourceExecutor {

    private final long minSourceCallMs;

    public SourceExecutor(long minSourceCallMs) {
        this.minSourceCallMs = minSourceCallMs;
    }

    public SourceExecutor() {
        this(1000L);
    }

    /** Синхронный последовательный обход (ADR-VL-15). Бюджет — из RequestContext (ADR-VL-16). */
    public <T> List<PrincipalResult<T>> execute(List<SourceCall<T>> calls, RequestContext ctx) {
        List<PrincipalResult<T>> results = new ArrayList<>();
        if (calls == null || calls.isEmpty()) {
            return results;
        }
        for (SourceCall<T> call : calls) {
            long remaining = ctx.budgetDeadlineAt() == null
                    ? Long.MAX_VALUE
                    : ctx.remainingBudgetMs();
            if (remaining <= minSourceCallMs) {
                results.add(PrincipalResult.skippedNoBudget(call.sourceId()));
                continue;
            }
            try {
                results.add(call.call().get());
            } catch (Exception e) {
                results.add(PrincipalResult.failure(call.sourceId(),
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 0L));
            }
        }
        return results;
    }
}
