package com.workspace.storm.event.entity.atlas;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AtlasSearchResult {

    private List<AtlasRide> rides = new ArrayList<>();

    private int progressCompleted;
    private int progressTotal;
    private boolean progressReached;

    private boolean partial;
    private int totalRides;
    private long durationMs;
    private List<String> succeeded = new ArrayList<>();
    private List<String> failed = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

}