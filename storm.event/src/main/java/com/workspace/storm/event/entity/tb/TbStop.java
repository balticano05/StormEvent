package com.workspace.storm.event.entity.tb;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TbStop {

    private String stationId;
    private String name;
    private String distance;
    private String departure;
    private String arrival;
    private String address;

}