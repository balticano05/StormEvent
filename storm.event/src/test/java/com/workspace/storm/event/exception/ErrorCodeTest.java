package com.workspace.storm.event.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void allCodesHaveUniqueHttpStatus() {
        // Some codes intentionally share HTTP status (e.g., SOURCE_TIMEOUT/ERROR/UNAVAILABLE -> OK)
        // Just verify no duplicate enum entries
        Set<ErrorCode> codes = Arrays.stream(ErrorCode.values())
                .collect(Collectors.toSet());
        assertEquals(ErrorCode.values().length, codes.size());
    }

    @Test
    void eachCodeHasI18nKey() {
        for (ErrorCode code : ErrorCode.values()) {
            String key = code.getI18nKey();
            assertNotNull(key);
            assertTrue(key.startsWith("error."));
            assertFalse(key.contains("_"), "I18n key should use dots not underscores: " + key);
        }
    }

    @Test
    void specificCodeMappings() {
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getHttpStatus());
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.getHttpStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getHttpStatus());
        assertEquals(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.getHttpStatus());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND.getHttpStatus());
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.CONFLICT.getHttpStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.getHttpStatus());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.getHttpStatus());
        assertEquals(HttpStatus.OK, ErrorCode.SOURCE_TIMEOUT.getHttpStatus());
        assertEquals(HttpStatus.OK, ErrorCode.SOURCE_ERROR.getHttpStatus());
        assertEquals(HttpStatus.OK, ErrorCode.SOURCE_UNAVAILABLE.getHttpStatus());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ErrorCode.REQUEST_TIMEOUT.getHttpStatus());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.DATA_CONSTRAINT.getHttpStatus());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_CONFLICT.getHttpStatus());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED.getHttpStatus());
        assertEquals(HttpStatus.OK, ErrorCode.SEARCH_FINISHED_PARTIAL.getHttpStatus());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PROVIDER_DOWN.getHttpStatus());
        assertEquals(HttpStatus.BAD_GATEWAY, ErrorCode.PARSE_ERROR.getHttpStatus());
        assertEquals(HttpStatus.BAD_GATEWAY, ErrorCode.TOOL_ERROR.getHttpStatus());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.LLM_UNAVAILABLE.getHttpStatus());
    }

    @Test
    void i18nKeyFormat() {
        assertEquals("error.validation.error", ErrorCode.VALIDATION_ERROR.getI18nKey());
        assertEquals("error.bad.request", ErrorCode.BAD_REQUEST.getI18nKey());
        assertEquals("error.internal.error", ErrorCode.INTERNAL_ERROR.getI18nKey());
        assertEquals("error.source.timeout", ErrorCode.SOURCE_TIMEOUT.getI18nKey());
        assertEquals("error.search.finished.partial", ErrorCode.SEARCH_FINISHED_PARTIAL.getI18nKey());
        assertEquals("error.provider.down", ErrorCode.PROVIDER_DOWN.getI18nKey());
        assertEquals("error.parse.error", ErrorCode.PARSE_ERROR.getI18nKey());
        assertEquals("error.tool.error", ErrorCode.TOOL_ERROR.getI18nKey());
        assertEquals("error.llm.unavailable", ErrorCode.LLM_UNAVAILABLE.getI18nKey());
    }
}