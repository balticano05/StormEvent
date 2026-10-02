package com.workspace.storm.event.llm;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Детерминированный stub до появления ключа OpenRouter (У-4). */
@Component
@Primary
public class LlmGatewayStub implements LlmGateway {

    public static final String STUB_MARK = "[stub]";

    @Override
    public LlmResponse complete(LlmRequest request) {
        String last = request.history().isEmpty() ? "" : request.history().get(request.history().size() - 1);
        return LlmResponse.text(STUB_MARK + " " + last, 0L);
    }
}
