package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbSchedule;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
            Optional.ofNullable(route.parent())
                    .map(this::parseRow)
                    .ifPresent(schedules::add);
        }
        return schedules;
    }

    private TbSchedule parseRow(Element row) {
        List<Element> cells = row.children();
        TbSchedule schedule = new TbSchedule();
        Element route = Parsers.cell(cells, 1);
        schedule.setCode(Optional.ofNullable(route)
                .map(Element::outerHtml)
                .flatMap(TicketBusScheduleParser::clickCode)
                .orElse(null));
        schedule.setRoute(Parsers.ownText(route).orElse(null));
        schedule.setAvailability(availability(route).orElse(null));
        schedule.setForward(Parsers.text(Parsers.cell(cells, 2)).orElse(null));
        schedule.setBackward(Parsers.text(Parsers.cell(cells, 3)).orElse(null));
        schedule.setPeriodicity(Parsers.text(Parsers.cell(cells, 4)).orElse(null));
        schedule.setAddress(Parsers.text(Parsers.cell(cells, 5)).orElse(null));
        return schedule;
    }

    private Optional<String> availability(Element route) {
        return Optional.ofNullable(route)
                .map(r -> r.selectFirst("font"))
                .flatMap(Parsers::text);
    }

    private static Optional<String> clickCode(String html) {
        Matcher m = CLICK_CODE.matcher(html);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

}
