package com.workspace.storm.event.dto.source;

public enum SourceStatus {
    OK,
    FAILURE,
    PARTIAL,
    TIMEOUT,
    SKIPPED,
    SKIPPED_NO_BUDGET
}
