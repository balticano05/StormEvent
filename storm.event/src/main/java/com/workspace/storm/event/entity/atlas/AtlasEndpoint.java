package com.workspace.storm.event.entity.atlas;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AtlasEndpoint {

    private String id;
    private String desc;
    private String timezone;
    private String countryCode;

}