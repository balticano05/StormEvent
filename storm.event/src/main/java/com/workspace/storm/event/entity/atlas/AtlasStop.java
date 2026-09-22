package com.workspace.storm.event.entity.atlas;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AtlasStop {

    private String id;
    private String desc;
    private String info;
    private Double longitude;
    private Double latitude;
    private boolean important;
    private String timezone;
    private boolean dynamic;
    private boolean seating;
    private String datetime;
    private String city;
    private String country;

}