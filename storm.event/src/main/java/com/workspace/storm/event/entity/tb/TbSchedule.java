package com.workspace.storm.event.entity.tb;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TbSchedule {

    private String code;
    private String route;
    private String forward;
    private String backward;
    private String availability;
    private String periodicity;
    private String address;

}