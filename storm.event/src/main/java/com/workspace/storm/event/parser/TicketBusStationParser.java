package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbStation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class TicketBusStationParser {

    public List<TbStation> parse(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<TbStation> stations = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 3 || parts[0].isBlank()) {
                continue;
            }
            TbStation station = new TbStation();
            station.setId(parts[0]);
            station.setName(parts[1]);
            station.setDescription(parts[2]);
            stations.add(station);
        }
        return stations;
    }

}