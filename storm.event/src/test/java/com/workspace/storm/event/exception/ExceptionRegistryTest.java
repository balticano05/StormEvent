package com.workspace.storm.event.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionRegistryTest {

    @ParameterizedTest
    @MethodSource("exceptionCodePairs")
    void resolvesCorrectCodeForException(Throwable exception, ErrorCode expectedCode) {
        ErrorCode resolved = ExceptionRegistry.resolve(exception);
        assertEquals(expectedCode, resolved, "Failed for " + exception.getClass().getSimpleName());
    }

    static Stream<Arguments> exceptionCodePairs() {
        return Stream.of(
                Arguments.of(new AtlasClientException("msg"), ErrorCode.SOURCE_ERROR),
                Arguments.of(new AtlasServiceException("msg"), ErrorCode.SOURCE_ERROR),
                Arguments.of(new BelHotelClientException("msg"), ErrorCode.SOURCE_ERROR),
                Arguments.of(new BzdClientException("msg"), ErrorCode.SOURCE_ERROR),
                Arguments.of(new TicketBusClientException("msg"), ErrorCode.SOURCE_ERROR),
                Arguments.of(new TicketBusServiceException("msg"), ErrorCode.SOURCE_ERROR),
                Arguments.of(new TicketProClientException("msg"), ErrorCode.SOURCE_ERROR),
                Arguments.of(new TicketProServiceException("msg"), ErrorCode.SOURCE_ERROR),
                Arguments.of(new ParseException("msg", null), ErrorCode.PARSE_ERROR),
                Arguments.of(new ToolException("msg"), ErrorCode.TOOL_ERROR),
                Arguments.of(new LlmException("msg"), ErrorCode.LLM_UNAVAILABLE)
        );
    }

    @Test
    void stormExceptionReturnsItsOwnCode() {
        StormException ex = new StormException(ErrorCode.REQUEST_TIMEOUT, "timeout") {};
        assertEquals(ErrorCode.REQUEST_TIMEOUT, ExceptionRegistry.resolve(ex));
    }

    @Test
    void unknownExceptionDefaultsToInternalError() {
        ErrorCode resolved = ExceptionRegistry.resolve(new IllegalStateException("unknown"));
        assertEquals(ErrorCode.INTERNAL_ERROR, resolved);
    }

    @Test
    void nullExceptionThrowsNPE() {
        assertThrows(NullPointerException.class, () -> ExceptionRegistry.resolve(null));
    }
}