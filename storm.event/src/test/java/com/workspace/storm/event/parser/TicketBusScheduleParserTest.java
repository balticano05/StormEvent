package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbSchedule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketBusScheduleParserTest {

    private final TicketBusScheduleParser parser = new TicketBusScheduleParser();

    @Test
    void parsesScheduleRows() {
        String html = """
                <tr class='even'>
                  <td class='time'></td>
                  <td class='marshrut' onClick="showclick('21600xxx357`9~20250320','1','1')">МИНСК АВ Центральный-ГРОДНО АВ<br><font color='#00FF00'>в продаже</font></td>
                  <td class='time'>06:00/09:01</td>
                  <td class='time'></td>
                  <td class='typ'> Ежедневно</td>
                  <td>МИНСК Беларусь-ГРОДНО ГРОДНЕНСКАЯ ОБЛ. Беларусь</td>
                </tr>
                <tr><td></td><td colspan='5' id='raspp1' style='display: table-cell;'></td></tr>
                """;

        List<TbSchedule> schedules = parser.parse(html);

        assertEquals(1, schedules.size());
        TbSchedule schedule = schedules.get(0);
        assertEquals("21600xxx357`9~20250320", schedule.getCode());
        assertEquals("МИНСК АВ Центральный-ГРОДНО АВ", schedule.getRoute());
        assertEquals("06:00/09:01", schedule.getForward());
        assertEquals("в продаже", schedule.getAvailability());
        assertEquals("Ежедневно", schedule.getPeriodicity());
        assertEquals("МИНСК Беларусь-ГРОДНО ГРОДНЕНСКАЯ ОБЛ. Беларусь", schedule.getAddress());
    }

    @Test
    void capturesBusSaleNote() {
        String html = """
                <tr class='odd'>
                  <td class='time'></td>
                  <td class='marshrut' onClick="showclick('22500xxx305`11~20221228','1','2')">МИНСК АВ Центральный-ГРОДНО АВ<br><font color='#FF0000'>продажа только в автобусе</font></td>
                  <td class='time'>06:15/09:02</td>
                  <td class='time'></td>
                  <td class='typ'> Ежедневно<br>отменяется с 01.11.26</td>
                  <td>МИНСК Беларусь-ГРОДНО ГРОДНЕНСКАЯ ОБЛ. Беларусь</td>
                </tr>
                """;

        TbSchedule schedule = parser.parse(html).get(0);

        assertEquals("продажа только в автобусе", schedule.getAvailability());
        assertEquals("Ежедневно отменяется с 01.11.26", schedule.getPeriodicity());
    }

    @Test
    void returnsEmptyOnBlank() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
    }

}