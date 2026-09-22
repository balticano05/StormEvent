package com.workspace.storm.event.entity.tb;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class TbRace {

    private String code;
    private String route;
    private Integer seats;
    private BigDecimal price;
    private String departure;
    private String arrival;
    private String busModel;
    private String seatType;
    private String carrier;

}