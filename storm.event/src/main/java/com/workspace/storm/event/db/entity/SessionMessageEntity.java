package com.workspace.storm.event.db.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class SessionMessageEntity {
    private Long id;
    private UUID sessionId;
    private String role;
    private String kind;
    private String text;
    private UUID requestId;
    private Instant createdAt;
}