package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.atlas.AtlasStation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasStationParserTest {

    private final AtlasStationParser parser = new AtlasStationParser();

    @Test
    void parsesStationArray() {
        String json = """
                [{"id":"c627904","description":"Гродненская область","country":"BY","name":"Гродно","latitude":53.6781,"longitude":23.8438}]
                """;

        List<AtlasStation> stations = parser.parse(json);

        assertEquals(1, stations.size());
        AtlasStation s = stations.get(0);
        assertEquals("c627904", s.getId());
        assertEquals("Гродно", s.getName());
        assertEquals("BY", s.getCountry());
        assertEquals(53.6781, s.getLatitude(), 1e-4);
    }

    @Test
    void returnsEmptyOnMalformedJson() {
        List<AtlasStation> stations = parser.parse("not-a-json");
        assertTrue(stations.isEmpty());
    }

}