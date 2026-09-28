package com.workspace.storm.event.db.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class StatsSourceHourlyEntity {
    private Instant hour;
    private String source;
    private String code;
    private int count;
}