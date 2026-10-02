package com.workspace.storm.event.llm;

import java.util.List;

public record LlmRequest(String systemPrompt, List<String> history, List<String> toolNames, long timeoutMs) {

    public LlmRequest {
        history = history == null ? List.of() : List.copyOf(history);
        toolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
    }
}
