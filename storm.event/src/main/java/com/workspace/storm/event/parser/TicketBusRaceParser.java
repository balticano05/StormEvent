package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbRace;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
            Optional.ofNullable(route.parent())
                    .map(this::parseRow)
                    .ifPresent(races::add);
        }
        return races;
    }

    private TbRace parseRow(Element row) {
        List<Element> cells = row.children();
        TbRace race = new TbRace();
        race.setCode(code(cells).orElse(null));
        race.setRoute(text(cells, 1).orElse(null));
        race.setSeats(text(cells, 2).flatMap(Parsers::toInt).orElse(null));
        race.setPrice(text(cells, 3).flatMap(Parsers::toDecimal).orElse(null));
        race.setDeparture(text(cells, 4).orElse(null));
        race.setArrival(text(cells, 5).orElse(null));
        race.setBusModel(text(cells, 6).orElse(null));
        parseInfo(Parsers.cell(cells, 7), race);
        return race;
    }

    private void parseInfo(Element info, TbRace race) {
        if (info == null) {
            return;
        }
        race.setSeatType(Parsers.ownText(info).orElse(null));
        Optional.ofNullable(info.selectFirst("font"))
                .flatMap(Parsers::text)
                .ifPresent(race::setCarrier);
    }

    private Optional<String> code(List<Element> cells) {
        Optional<String> fromInput = Optional.ofNullable(Parsers.cell(cells, 0))
                .map(order -> order.selectFirst("input[name='modal']"))
                .map(input -> input.attr("id"))
                .filter(id -> !id.isBlank());
        if (fromInput.isPresent()) {
            return fromInput;
        }
        return Optional.ofNullable(Parsers.cell(cells, 1))
                .map(Element::outerHtml)
                .flatMap(TicketBusRaceParser::clickCode);
    }

    private static Optional<String> clickCode(String html) {
        Matcher m = CLICK_CODE.matcher(html);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static Optional<String> text(List<Element> cells, int index) {
        return Parsers.text(Parsers.cell(cells, index));
    }

}
