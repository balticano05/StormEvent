package com.workspace.storm.event.dto.source;

import java.util.List;

public final class PrincipalResult<T> {

    private final SourceStatus status;
    private final T data;
    private final List<String> errors;
    private final boolean stale;
    private final long durationMs;
    private final String sourceId;

    private PrincipalResult(SourceStatus status, T data, List<String> errors, boolean stale, long durationMs, String sourceId) {
        this.status = status;
        this.data = data;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.stale = stale;
        this.durationMs = durationMs;
        this.sourceId = sourceId;
    }

    public static <T> PrincipalResult<T> ok(String sourceId, T data, long durationMs) {
        return new PrincipalResult<>(SourceStatus.OK, data, List.of(), false, durationMs, sourceId);
    }

    public static <T> PrincipalResult<T> failure(String sourceId, String error, long durationMs) {
        return new PrincipalResult<>(SourceStatus.FAILURE, null, List.of(error), false, durationMs, sourceId);
    }

    public static <T> PrincipalResult<T> partial(String sourceId, T data, List<String> errors, long durationMs) {
        return new PrincipalResult<>(SourceStatus.PARTIAL, data, errors, false, durationMs, sourceId);
    }

    public static <T> PrincipalResult<T> timeout(String sourceId, long durationMs) {
        return new PrincipalResult<>(SourceStatus.TIMEOUT, null, List.of("timeout"), false, durationMs, sourceId);
    }

    public static <T> PrincipalResult<T> skipped(String sourceId, String reason) {
        return new PrincipalResult<>(SourceStatus.SKIPPED, null, List.of(reason), false, 0L, sourceId);
    }

    public static <T> PrincipalResult<T> skippedNoBudget(String sourceId) {
        return new PrincipalResult<>(SourceStatus.SKIPPED_NO_BUDGET, null, List.of("no budget remaining"), false, 0L, sourceId);
    }

    public SourceStatus getStatus() { return status; }
    public T getData() { return data; }
    public List<String> getErrors() { return errors; }
    public boolean isStale() { return stale; }
    public long getDurationMs() { return durationMs; }
    public String getSourceId() { return sourceId; }

    public boolean isOk() { return status == SourceStatus.OK || status == SourceStatus.PARTIAL; }
}
