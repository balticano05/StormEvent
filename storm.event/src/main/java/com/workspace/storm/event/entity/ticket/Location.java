package com.workspace.storm.event.entity.ticket;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Location {

    private String name;
    private Address address;

}