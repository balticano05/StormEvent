package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbRace;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TicketBusRaceParser {

    private static final String ROW_SELECTOR = "td.marshrut";
    private static final Pattern CLICK_CODE = Pattern.compile("showclick\\('([^']+)'");

    public List<TbRace> parse(String html) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }
        Document doc = Jsoup.parse("<table><tbody>" + html + "</tbody></table>");
        List<TbRace> races = new ArrayList<>();
        for (Element route : doc.select(ROW_SELECTOR)) {
            Element row = route.parent();
            if (row != null) {
                races.add(parseRow(row));
            }
        }
        return races;
    }

    private TbRace parseRow(Element row) {
        List<Element> cells = row.children();
        TbRace race = new TbRace();
        race.setCode(code(cells));
        race.setRoute(text(cell(cells, 1)));
        race.setSeats(parseSeats(text(cell(cells, 2))));
        race.setPrice(parsePrice(text(cell(cells, 3))));
        race.setDeparture(text(cell(cells, 4)));
        race.setArrival(text(cell(cells, 5)));
        race.setBusModel(text(cell(cells, 6)));
        parseInfo(cell(cells, 7), race);
        return race;
    }

    private void parseInfo(Element info, TbRace race) {
        if (info == null) {
            return;
        }
        race.setSeatType(ownText(info));
        Element font = info.selectFirst("font");
        if (font != null) {
            String carrier = text(font);
            if (!carrier.isBlank()) {
                race.setCarrier(carrier);
            }
        }
    }

    private String code(List<Element> cells) {
        Element order = cell(cells, 0);
        if (order != null) {
            Element input = order.selectFirst("input[name='modal']");
            String id = input == null ? null : input.attr("id");
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        Element route = cell(cells, 1);
        if (route != null) {
            Matcher m = CLICK_CODE.matcher(route.outerHtml());
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private Integer parseSeats(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().chars().allMatch(Character::isDigit) ? Integer.parseInt(raw.trim()) : null;
    }

    private BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String ownText(Element el) {
        if (el == null) {
            return null;
        }
        String value = el.ownText().replace('\u00A0', ' ').trim();
        return value.isBlank() ? null : value;
    }

    private String text(Element el) {
        if (el == null) {
            return null;
        }
        String value = el.text().replace('\u00A0', ' ').trim();
        return value.isBlank() ? null : value;
    }

    private Element cell(List<Element> cells, int index) {
        return index < cells.size() ? cells.get(index) : null;
    }

}