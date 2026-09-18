package com.workspace.storm.event.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Address {

    private String streetAddress;
    private String addressLocality;
    private String addressRegion;
    private String addressCountry;

}