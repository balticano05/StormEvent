package com.workspace.storm.event.entity.ticket;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class Offer {

    private String availability;
    private BigDecimal price;
    private BigDecimal lowPrice;
    private BigDecimal highPrice;
    private String priceCurrency;
    private String url;
    private String validFrom;

}