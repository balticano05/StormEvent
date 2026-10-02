package com.workspace.storm.event.orchestration;

import java.util.List;

/** Sequential list of tool invocations for a request (ADR-VL-15). */
public record RunPlan(List<String> toolNames) {

    public static RunPlan of(String... toolNames) {
        return new RunPlan(List.of(toolNames));
    }

    public boolean isEmpty() {
        return toolNames == null || toolNames.isEmpty();
    }
}
