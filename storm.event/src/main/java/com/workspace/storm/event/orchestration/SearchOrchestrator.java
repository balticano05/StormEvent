package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.dto.source.ToolResult;

/** MVP orchestrator: empty tool set short-circuits (full loop arrives with the LLM agent). */
public class SearchOrchestrator {

    public ToolResult run(PipelineContext ctx, RunPlan plan) {
        if (plan == null || plan.isEmpty()) {
            return ToolResult.empty("search");
        }
        return ToolResult.empty("search");
    }
}
