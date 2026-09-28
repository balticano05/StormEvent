package com.workspace.storm.event.db.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class CachedResultEntity {
    private String cacheKey;
    private String source;
    private String domain;
    private String payloadJson;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean stale;
}