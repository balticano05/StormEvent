package com.workspace.storm.event.entity.atlas;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AtlasStation {

    private String id;
    private String description;
    private String country;
    private String name;
    private Double latitude;
    private Double longitude;

}