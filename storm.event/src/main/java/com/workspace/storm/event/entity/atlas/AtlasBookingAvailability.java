package com.workspace.storm.event.entity.atlas;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AtlasBookingAvailability {

    @JsonProperty("isAvailable")
    private Boolean isAvailable;
    private Object ticketsLimit;
    private Object remainingCapacity;
    private String salesOpenAt;

}