package com.workspace.storm.event.entity.bzd;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class BzdCar {

    private String name;
    private Integer seats;
    private BigDecimal priceByn;

}