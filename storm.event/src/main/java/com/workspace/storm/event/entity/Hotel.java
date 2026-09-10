package com.workspace.storm.event.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Hotel {

    private String id;
    private Long cityId;
    private String name;
    private String type;
    private String category;
    private int stars;
    private List<RoomOffer> offers = new ArrayList<>();

}
