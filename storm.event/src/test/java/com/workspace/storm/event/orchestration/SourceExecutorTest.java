package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.config.TimeoutProperties;
import com.workspace.storm.event.context.RequestContext;
import com.workspace.storm.event.dto.source.PrincipalResult;
import com.workspace.storm.event.dto.source.SourceStatus;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class SourceExecutorTest {

    private RequestContext ctx(long softBudgetMs) {
        TimeoutProperties props = new TimeoutProperties();
        props.setSoftBudgetMs(softBudgetMs);
        props.setHardCeilingMs(softBudgetMs + 20000);
        return RequestContext.create("req-1", props, "ru", null);
    }

    @Test
    void emptyCallSetReturnsEmpty() {
        SourceExecutor exec = new SourceExecutor();
        assertTrue(exec.execute(List.of(), ctx(40000)).isEmpty());
    }

    @Test
    void resultsAreInCallOrder() {
        SourceExecutor exec = new SourceExecutor();
        List<String> order = new ArrayList<>();
        List<SourceCall<String>> calls = List.of(
                new SourceCall<>("a", () -> { order.add("a"); return PrincipalResult.ok("a", "1", 1); }),
                new SourceCall<>("b", () -> { order.add("b"); return PrincipalResult.ok("b", "2", 1); }));
        List<PrincipalResult<String>> results = exec.execute(calls, ctx(40000));
        assertEquals(List.of("a", "b"), order);
        assertEquals(List.of("a", "b"), results.stream().map(PrincipalResult::getSourceId).toList());
    }

    @Test
    void noBudgetSkipsRemaining() {
        SourceExecutor exec = new SourceExecutor(1000);
        List<SourceCall<String>> calls = List.of(
                new SourceCall<>("a", () -> PrincipalResult.ok("a", "1", 1)),
                new SourceCall<>("b", () -> PrincipalResult.ok("b", "2", 1)));
        List<PrincipalResult<String>> results = exec.execute(calls, ctx(5000));
        assertTrue(results.stream().allMatch(r -> r.getStatus() == SourceStatus.SKIPPED_NO_BUDGET || r.getStatus() == SourceStatus.OK));
    }

    @Test
    void exceptionBecomesFailure() {
        SourceExecutor exec = new SourceExecutor();
        List<SourceCall<String>> calls = List.of(
                new SourceCall<>("a", () -> { throw new IllegalStateException("boom"); }),
                new SourceCall<>("b", () -> PrincipalResult.ok("b", "2", 1)));
        List<PrincipalResult<String>> results = exec.execute(calls, ctx(40000));
        assertEquals(SourceStatus.FAILURE, results.get(0).getStatus());
        assertEquals(SourceStatus.OK, results.get(1).getStatus());
    }
}
