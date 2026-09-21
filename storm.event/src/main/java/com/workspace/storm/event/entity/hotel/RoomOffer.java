package com.workspace.storm.event.entity.hotel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class RoomOffer {

    private String roomType;
    private int capacity;
    private String imageUrl;
    private BigDecimal price;
    private String currency;
    private boolean breakfastIncluded;
    private String bookingUrl;

}