package com.workspace.storm.event.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class RoomOffer {

    private String roomType;
    private Integer capacity;
    private BigDecimal price;
    private String currency;
    private boolean breakfastIncluded;
    private String imageUrl;
    private String bookingUrl;

}
