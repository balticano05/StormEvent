package com.workspace.storm.event.llm;

import java.util.List;

public record LlmResponse(String text, List<ToolCall> toolCalls, long durationMs, boolean failed) {

    public LlmResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static LlmResponse text(String text, long durationMs) {
        return new LlmResponse(text, List.of(), durationMs, false);
    }

    public static LlmResponse failure() {
        return new LlmResponse("", List.of(), 0L, true);
    }
}
