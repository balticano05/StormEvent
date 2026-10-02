package com.workspace.storm.event.llm;

public interface LlmGateway {

    LlmResponse complete(LlmRequest request);
}
