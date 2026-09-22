package com.workspace.storm.event.entity.bzd;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class BzdTrain {

    private String number;
    private String type;
    private String fromTime;
    private String fromName;
    private String toTime;
    private String toName;
    private String duration;
    private List<BzdCar> cars = new ArrayList<>();

}