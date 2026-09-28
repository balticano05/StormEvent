package com.workspace.storm.event.db.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class IdempotencyEntity {
    private UUID requestId;
    private String responseJson;
    private UUID sessionId;
    private Instant createdAt;
    private Instant expiresAt;
}