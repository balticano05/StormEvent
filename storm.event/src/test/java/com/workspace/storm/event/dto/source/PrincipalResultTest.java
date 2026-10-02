package com.workspace.storm.event.dto.source;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class PrincipalResultTest {

    @Test
    void okCarriesData() {
        PrincipalResult<String> r = PrincipalResult.ok("atlasbus", "payload", 12L);
        assertEquals(SourceStatus.OK, r.getStatus());
        assertEquals("payload", r.getData());
        assertTrue(r.getErrors().isEmpty());
        assertTrue(r.isOk());
    }

    @Test
    void failureCarriesError() {
        PrincipalResult<String> r = PrincipalResult.failure("bzd", "403", 5L);
        assertEquals(SourceStatus.FAILURE, r.getStatus());
        assertNull(r.getData());
        assertEquals(List.of("403"), r.getErrors());
        assertFalse(r.isOk());
    }

    @Test
    void partialIsOk() {
        PrincipalResult<String> r = PrincipalResult.partial("atlasbus", "x", List.of("late chunk"), 30L);
        assertEquals(SourceStatus.PARTIAL, r.getStatus());
        assertTrue(r.isOk());
        assertEquals(1, r.getErrors().size());
    }

    @Test
    void timeoutAndSkipped() {
        assertEquals(SourceStatus.TIMEOUT, PrincipalResult.timeout("bzd", 100L).getStatus());
        assertEquals(SourceStatus.SKIPPED, PrincipalResult.skipped("bzd", "off").getStatus());
        assertEquals(SourceStatus.SKIPPED_NO_BUDGET, PrincipalResult.<String>skippedNoBudget("bzd").getStatus());
    }
}
