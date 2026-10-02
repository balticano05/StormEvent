package com.workspace.storm.event.dto.source;

import com.workspace.storm.event.dto.offer.Offer;

import java.util.List;

public record ToolResult(
        String domain,
        List<Offer> offers,
        List<PrincipalResult<?>> sourceResults,
        List<String> warnings,
        long durationMs
) {
    public static ToolResult empty(String domain) {
        return new ToolResult(domain, List.of(), List.of(), List.of(), 0L);
    }
}
