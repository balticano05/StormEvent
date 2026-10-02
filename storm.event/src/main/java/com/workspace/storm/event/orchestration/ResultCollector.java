package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.dto.source.SourceStatus;
import com.workspace.storm.event.dto.source.ToolResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Собирает PrincipalResult'ы в ToolResult: офферы + warnings (дедуп, limit 5) + статусы. */
public final class ResultCollector {

    private static final int WARNING_LIMIT = 5;

    private ResultCollector() {
    }

    public static ToolResult collect(String domain, List<PrincipalResult<List<Offer>>> results, long durationMs) {
        List<Offer> offers = new ArrayList<>();
        Set<String> warnings = new LinkedHashSet<>();
        for (PrincipalResult<List<Offer>> result : results) {
            if (result.getData() != null) {
                offers.addAll(result.getData());
            }
            if (result.getStatus() != SourceStatus.OK && result.getStatus() != SourceStatus.PARTIAL) {
                warnings.add(messageFor(result));
            } else {
                warnings.addAll(result.getErrors());
            }
        }
        List<String> warningsList = new ArrayList<>(warnings);
        if (warningsList.size() > WARNING_LIMIT) {
            warningsList = new ArrayList<>(warningsList.subList(0, WARNING_LIMIT));
        }
        @SuppressWarnings("unchecked")
        List<PrincipalResult<?>> sources = new ArrayList<>(results);
        return new ToolResult(domain, offers, sources, warningsList, durationMs);
    }

    private static String messageFor(PrincipalResult<?> result) {
        return switch (result.getStatus()) {
            case SKIPPED -> result.getSourceId() + ": отключён";
            case SKIPPED_NO_BUDGET -> result.getSourceId() + ": не уложелись в бюджет времени";
            case TIMEOUT -> result.getSourceId() + ": таймаут";
            default -> result.getSourceId() + ": " +
                    (result.getErrors().isEmpty() ? result.getStatus().name() : result.getErrors().get(0));
        };
    }
}
