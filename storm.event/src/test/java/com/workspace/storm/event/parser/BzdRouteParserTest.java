package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.bzd.BzdCar;
import com.workspace.storm.event.entity.bzd.BzdTrain;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BzdRouteParserTest {

    private final BzdRouteParser parser = new BzdRouteParser();

    @Test
    void parsesTrainRows() {
        String html = """
                <div class="sch-table">
                  <div class="sch-table__row interregional_economy" data-train-number="610Б" data-train-type="interregional_economy">
                    <div class="sch-table__left">
                      <span class="train-number-value">610Б</span>
                    </div>
                    <div class="sch-table__center">
                      <div class="sch-table__dep">
                        <span class="sch-table__time train-from-time">19:16</span>
                        <span class="sch-table__station train-from-name"><i class="ic-mark"></i>Гродно</span>
                      </div>
                      <div class="sch-table__arr">
                        <span class="sch-table__time train-to-time">07:41</span>
                        <span class="sch-table__station train-to-name">Брест-Центральный</span>
                      </div>
                      <span class="sch-table__duration train-duration-time">12ч 25мин</span>
                    </div>
                    <div class="sch-table__right">
                      <div class="sch-table__t-item">
                        <div class="sch-table__t-name">Плацкартный</div>
                        <div class="sch-table__t-quant"><span>30</span></div>
                        <a class="sch-table__t-link" data-cost-byn="29,13" href="/ru/order/places/">выбрать</a>
                      </div>
                      <div class="sch-table__t-item">
                        <div class="sch-table__t-name">Купейный</div>
                        <div class="sch-table__t-quant"><span>18</span></div>
                        <a class="sch-table__t-link" data-cost-byn="49,14" href="/ru/order/places/">выбрать</a>
                      </div>
                    </div>
                  </div>
                  <div class="sch-table__row city" data-train-number="783Б">
                    <div class="sch-table__center">
                      <div class="sch-table__dep">
                        <span class="sch-table__time train-from-time">07:00</span>
                        <span class="sch-table__station train-from-name">Брест</span>
                      </div>
                      <div class="sch-table__arr">
                        <span class="sch-table__time train-to-time">09:05</span>
                        <span class="sch-table__station train-to-name">Гродно</span>
                      </div>
                    </div>
                  </div>
                </div>
                """;

        List<BzdTrain> trains = parser.parse(html);

        assertEquals(2, trains.size());

        BzdTrain first = trains.get(0);
        assertEquals("610Б", first.getNumber());
        assertEquals("interregional_economy", first.getType());
        assertEquals("19:16", first.getFromTime());
        assertEquals("Гродно", first.getFromName());
        assertEquals("07:41", first.getToTime());
        assertEquals("Брест-Центральный", first.getToName());
        assertEquals("12ч 25мин", first.getDuration());

        assertEquals(2, first.getCars().size());
        BzdCar platzkart = first.getCars().get(0);
        assertEquals("Плацкартный", platzkart.getName());
        assertEquals(Integer.valueOf(30), platzkart.getSeats());
        assertEquals(new BigDecimal("29.13"), platzkart.getPriceByn());
        assertEquals(new BigDecimal("49.14"), first.getCars().get(1).getPriceByn());

        BzdTrain second = trains.get(1);
        assertEquals("783Б", second.getNumber());
        assertEquals("city", second.getType());
        assertNotNull(second.getCars());
        assertTrue(second.getCars().isEmpty());
    }

    @Test
    void returnsEmptyOnBlank() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
    }

}