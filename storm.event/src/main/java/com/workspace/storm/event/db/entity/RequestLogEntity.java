package com.workspace.storm.event.db.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class RequestLogEntity {
    private Long id;
    private UUID requestId;
    private UUID sessionId;
    private String text;
    private String intentJson;
    private String status;
    private Integer durationMs;
    private Instant createdAt;
}