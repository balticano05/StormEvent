package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbSchedule;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TicketBusScheduleParser {

    private static final String ROW_SELECTOR = "td.marshrut";
    private static final Pattern CLICK_CODE = Pattern.compile("showclick\\('([^']+)'");

    public List<TbSchedule> parse(String html) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }
        Document doc = Jsoup.parse("<table><tbody>" + html + "</tbody></table>");
        List<TbSchedule> schedules = new ArrayList<>();
        for (Element route : doc.select(ROW_SELECTOR)) {
            Element row = route.parent();
            if (row != null) {
                schedules.add(parseRow(row));
            }
        }
        return schedules;
    }

    private TbSchedule parseRow(Element row) {
        List<Element> cells = row.children();
        TbSchedule schedule = new TbSchedule();
        Element route = cell(cells, 1);
        schedule.setCode(route == null ? null : clickCode(route.outerHtml()));
        schedule.setRoute(route == null ? null : ownText(route));
        schedule.setAvailability(availability(route));
        schedule.setForward(text(cell(cells, 2)));
        schedule.setBackward(text(cell(cells, 3)));
        schedule.setPeriodicity(periodicity(cell(cells, 4)));
        schedule.setAddress(text(cell(cells, 5)));
        return schedule;
    }

    private String availability(Element route) {
        if (route == null) {
            return null;
        }
        Element font = route.selectFirst("font");
        return font == null ? null : text(font);
    }

    private String periodicity(Element el) {
        if (el == null) {
            return null;
        }
        String value = el.text().replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        return value.isBlank() ? null : value;
    }

    private String clickCode(String html) {
        Matcher m = CLICK_CODE.matcher(html);
        return m.find() ? m.group(1) : null;
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