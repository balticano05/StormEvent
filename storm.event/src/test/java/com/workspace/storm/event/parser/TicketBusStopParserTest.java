package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbStop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketBusStopParserTest {

    private final TicketBusStopParser parser = new TicketBusStopParser();

    @Test
    void parsesStopsWithTimes() {
        String html = """
                <table id="stantion" class='list'>
                  <thead><tr><th>Станция</th><th>Расстояние</th><th>Отправление</th><th>Прибытие</th><th>Адрес</th></tr></thead>
                  <tbody>
                    <tr>
                      <td class='time'><input type='radio' name='rxradio' checked onclick='setstantionrx("500001");'></td>
                      <script type='text/javascript'> station_id1=500001;</script>
                      <td class='marshrut1'>МИНСК АВ Центральный</td>
                      <td class='time'>0 км.</td>
                      <td class='time'>16:20</td>
                      <td class='time'></td>
                      <td>МИНСК Беларусь</td>
                    </tr>
                    <tr>
                      <td class='time'></td>
                      <td class='marshrut1'>КОБРИН АВ</td>
                      <td class='time'>305 км.</td>
                      <td class='time'>20:13</td>
                      <td class='time'>20:03</td>
                      <td>КОБРИН Кобринский р-н БРЕСТСКАЯ ОБЛ. Беларусь</td>
                    </tr>
                  </tbody>
                </table>
                """;

        List<TbStop> stops = parser.parse(html);

        assertEquals(2, stops.size());
        TbStop first = stops.get(0);
        assertEquals("500001", first.getStationId());
        assertEquals("МИНСК АВ Центральный", first.getName());
        assertEquals("0 км.", first.getDistance());
        assertEquals("16:20", first.getDeparture());
        assertTrue(first.getArrival() == null || first.getArrival().isBlank());

        TbStop second = stops.get(1);
        assertEquals("КОБРИН АВ", second.getName());
        assertEquals("305 км.", second.getDistance());
        assertEquals("20:13", second.getDeparture());
        assertEquals("20:03", second.getArrival());
        assertEquals("КОБРИН Кобринский р-н БРЕСТСКАЯ ОБЛ. Беларусь", second.getAddress());
    }

    @Test
    void returnsEmptyOnEmptyTable() {
        String html = """
                <table id="stantion"><thead><tr><th>a</th><th>b</th></tr></thead><tbody></tbody></table>
                """;
        assertTrue(parser.parse(html).isEmpty());
    }

    @Test
    void returnsEmptyOnBlank() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
    }

}