package com.workspace.storm.event.db.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class SourceErrorLogEntity {
    private Long id;
    private UUID requestId;
    private String source;
    private String code;
    private String message;
    private Integer latencyMs;
    private Instant createdAt;
}