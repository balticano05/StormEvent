package com.workspace.storm.event.db.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class SessionEntity {
    private UUID id;
    private String intent;
    private Instant lastAccessAt;
    private Instant createdAt;
    private int ttlSeconds;
    private String state;
}