package com.workspace.storm.event.db.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class SourceStateEntity {
    private String source;
    private boolean enabled;
    private boolean draining;
    private Instant rampUntil;
    private Instant updatedAt;
}