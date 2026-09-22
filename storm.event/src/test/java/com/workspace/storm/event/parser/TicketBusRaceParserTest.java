package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbRace;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketBusRaceParserTest {

    private final TicketBusRaceParser parser = new TicketBusRaceParser();

    @Test
    void parsesRaceRows() {
        String html = """
                <tr class='odd'>
                  <td class='order'><input type='button' value='Купить' id='202609220615~AB~305`11' name='modal'></td>
                  <td class='marshrut' onClick="showclick('202609220615~AB~305`11','0','0')">МИНСК АВ Центральный - ГРОДНО АВ</td>
                  <td class='bus-info'>8</td>
                  <td class='price'>35.00</td>
                  <td class='time'>06:15</td>
                  <td class='time'>09:02</td>
                  <td class='typ'>Mersedes-Benz</td>
                  <td>Мягкий<br><font size='1'>ЧУП 'Владтрансавто' п. Самохваловичи +375-33-629-40-00</font></td>
                </tr>
                <tr><td></td><td colspan='7' id='rasp0'></td></tr>
                """;

        List<TbRace> races = parser.parse(html);

        assertEquals(1, races.size());
        TbRace race = races.get(0);
        assertEquals("202609220615~AB~305`11", race.getCode());
        assertEquals("МИНСК АВ Центральный - ГРОДНО АВ", race.getRoute());
        assertEquals(Integer.valueOf(8), race.getSeats());
        assertEquals(new BigDecimal("35.00"), race.getPrice());
        assertEquals("06:15", race.getDeparture());
        assertEquals("09:02", race.getArrival());
        assertEquals("Mersedes-Benz", race.getBusModel());
        assertEquals("Мягкий", race.getSeatType());
        assertTrue(race.getCarrier().startsWith("ЧУП 'Владтрансавто'"));
    }

    @Test
    void ignoresNoDataRow() {
        String html = "<tr><td colspan=\"20\" class=\"no-data\">Нет данных на данное число</td></tr>";
        assertTrue(parser.parse(html).isEmpty());
    }

    @Test
    void returnsEmptyOnBlank() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
    }

    @Test
    void handlesRowWithoutBusInfo() {
        String html = """
                <tr class='even'>
                  <td class='order'><input type='button' value='Купить' id='202609260835~AB~305`17' name='modal'></td>
                  <td class='marshrut' onClick="showclick('202609260835~AB~305`17','0','2')">МИНСК АВ Центральный - ГРОДНО АВ</td>
                  <td class='bus-info'></td>
                  <td class='price'>--</td>
                  <td class='time'>08:35</td>
                  <td class='time'>11:25</td>
                  <td class='typ'>Yutong</td>
                  <td>Мягкий</td>
                </tr>
                """;

        TbRace race = parser.parse(html).get(0);

        assertNull(race.getSeats());
        assertNull(race.getPrice());
        assertNull(race.getCarrier());
        assertEquals("Мягкий", race.getSeatType());
    }

}