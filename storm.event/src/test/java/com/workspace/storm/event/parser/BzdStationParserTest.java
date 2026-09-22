package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.bzd.BzdStation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BzdStationParserTest {

    private final BzdStationParser parser = new BzdStationParser();

    @Test
    void parsesAutocompleteArray() {
        String json = """
                [
                  {"id":9121,"value":"Гродно","exp":"2100070","ecp":"135208","gid":"628",
                   "label":"ст. Гродно, г. Гродно, Гродненская обл., Беларусь"},
                  {"id":9122,"value":"Гродеково","exp":"2100079","ecp":"135300","gid":"529"}
                ]
                """;

        List<BzdStation> stations = parser.parse(json);

        assertEquals(2, stations.size());
        BzdStation s = stations.get(0);
        assertEquals("Гродно", s.getValue());
        assertEquals("2100070", s.getExp());
        assertEquals("135208", s.getEcp());
        assertEquals("628", s.getGid());
    }

    @Test
    void returnsEmptyOnMalformedJson() {
        assertTrue(parser.parse("not-a-json").isEmpty());
    }

    @Test
    void returnsEmptyOnBlank() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
    }

}