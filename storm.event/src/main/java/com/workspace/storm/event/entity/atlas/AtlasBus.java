package com.workspace.storm.event.entity.atlas;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AtlasBus {

    private String mark;
    private String model;
    private String reg;
    private boolean hidden;

}