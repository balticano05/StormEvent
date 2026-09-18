package com.workspace.storm.event.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Event {

    private String url;
    private String name;
    private String startDate;
    private String endDate;
    private Location location;
    private List<String> images = new ArrayList<>();
    private String description;
    private Offer offers;

    private String categoryPath;

}