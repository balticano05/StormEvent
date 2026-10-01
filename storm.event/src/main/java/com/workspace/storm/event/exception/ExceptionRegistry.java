package com.workspace.storm.event.exception;

import java.util.Map;

public final class ExceptionRegistry {

    private static final Map<Class<? extends Throwable>, ErrorCode> EXCEPTION_TO_CODE = Map.ofEntries(
            Map.entry(AtlasClientException.class, ErrorCode.SOURCE_ERROR),
            Map.entry(AtlasServiceException.class, ErrorCode.SOURCE_ERROR),
            Map.entry(BelHotelClientException.class, ErrorCode.SOURCE_ERROR),
            Map.entry(BzdClientException.class, ErrorCode.SOURCE_ERROR),
            Map.entry(TicketBusClientException.class, ErrorCode.SOURCE_ERROR),
            Map.entry(TicketBusServiceException.class, ErrorCode.SOURCE_ERROR),
            Map.entry(TicketProClientException.class, ErrorCode.SOURCE_ERROR),
            Map.entry(TicketProServiceException.class, ErrorCode.SOURCE_ERROR),
            Map.entry(ParseException.class, ErrorCode.PARSE_ERROR),
            Map.entry(ToolException.class, ErrorCode.TOOL_ERROR),
            Map.entry(LlmException.class, ErrorCode.LLM_UNAVAILABLE)
    );

    private ExceptionRegistry() {
    }

    public static ErrorCode resolve(Throwable throwable) {
        if (throwable instanceof StormException stormEx) {
            return stormEx.getErrorCode();
        }
        Class<?> clazz = throwable.getClass();
        while (clazz != null && clazz != Throwable.class) {
            ErrorCode code = EXCEPTION_TO_CODE.get(clazz);
            if (code != null) {
                return code;
            }
            clazz = clazz.getSuperclass();
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}