package com.workspace.storm.event.entity.atlas;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AtlasFreighter {

    private Long id;
    private String name;
    private String inn;
    private String unp;
    private String address;
    private String regDate;
    private String authority;
    private String imsCarrierId;

}