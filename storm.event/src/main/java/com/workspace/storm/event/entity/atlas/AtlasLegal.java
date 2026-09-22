package com.workspace.storm.event.entity.atlas;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AtlasLegal {

    private String name;
    private String tin;
    private String address;
    private long taxId;
    private boolean isTicketRealizer;
    private List<String> supportPhones = new ArrayList<>();

}