package com.workspace.storm.event.llm;

public record ToolCall(String name, String argumentsJson) {
}
