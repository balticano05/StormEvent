package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbStation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketBusStationParserTest {

    private final TicketBusStationParser parser = new TicketBusStationParser();

    @Test
    void parsesPipeSeparatedLines() {
        String text = """
                417293|ЩУЧИН|ЩУЧИН Щучинский р-н ГРОДНЕНСКАЯ ОБЛ. Беларусь\r
                417001|ЩУЧИН АС|ЩУЧИН Щучинский р-н ГРОДНЕНСКАЯ ОБЛ. Беларусь\r
                100001|БРЕСТ АВ|БРЕСТ БРЕСТСКАЯ ОБЛ. Беларусь
                """;

        List<TbStation> stations = parser.parse(text);

        assertEquals(3, stations.size());
        TbStation first = stations.get(0);
        assertEquals("417293", first.getId());
        assertEquals("ЩУЧИН", first.getName());
        assertEquals("ЩУЧИН Щучинский р-н ГРОДНЕНСКАЯ ОБЛ. Беларусь", first.getDescription());
    }

    @Test
    void ignoresNoMatchesLine() {
        List<TbStation> stations = parser.parse("|нет совпадений|");
        assertTrue(stations.isEmpty());
    }

    @Test
    void returnsEmptyOnBlank() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
    }

}