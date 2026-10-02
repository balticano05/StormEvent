package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.context.RequestContext;

import java.util.Map;

public record PipelineContext(String sessionId, RequestContext requestContext, Map<String, Object> state) {

    public PipelineContext(String sessionId, RequestContext requestContext) {
        this(sessionId, requestContext, new java.util.HashMap<>());
    }
}
