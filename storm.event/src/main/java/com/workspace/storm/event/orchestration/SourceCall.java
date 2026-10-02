package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.dto.source.PrincipalResult;

import java.util.function.Supplier;

public record SourceCall<T>(String sourceId, Supplier<PrincipalResult<T>> call) {
}
