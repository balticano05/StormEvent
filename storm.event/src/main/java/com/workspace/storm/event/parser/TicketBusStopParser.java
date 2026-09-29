package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbStop;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TicketBusStopParser {

    private static final String ROW_SELECTOR = "#stantion > tbody > tr";
    private static final Pattern RADIO_ID = Pattern.compile("setstantionrx\\(\"(\\d+)\"\\)");
    private static final Pattern SCRIPT_ID = Pattern.compile("station_id1=(\\d+);");

    public List<TbStop> parse(String html) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }
        Document doc = Jsoup.parse(html);
        List<TbStop> stops = new ArrayList<>();
        for (Element row : doc.select(ROW_SELECTOR)) {
            parseRow(row).ifPresent(stops::add);
        }
        return stops;
    }

    private Optional<TbStop> parseRow(Element row) {
        Elements cells = Parsers.directTds(row);
        if (cells.size() < 2) {
            return Optional.empty();
        }
        TbStop stop = new TbStop();
        stop.setStationId(stationId(row, Parsers.cell(cells, 0)).orElse(null));
        stop.setName(Parsers.text(Parsers.cell(cells, 1)).orElse(null));
        stop.setDistance(Parsers.text(Parsers.cell(cells, 2)).orElse(null));
        stop.setDeparture(Parsers.text(Parsers.cell(cells, 3)).orElse(null));
        stop.setArrival(Parsers.text(Parsers.cell(cells, 4)).orElse(null));
        stop.setAddress(Parsers.text(Parsers.cell(cells, 5)).orElse(null));
        return Optional.of(stop);
    }

    private Optional<String> stationId(Element row, Element first) {
        Optional<String> fromRadio = Optional.ofNullable(first)
                .map(Element::outerHtml)
                .flatMap(TicketBusStopParser::firstGroupOf);
        if (fromRadio.isPresent()) {
            return fromRadio;
        }
        return Optional.ofNullable(row)
                .map(Element::html)
                .flatMap(TicketBusStopParser::scriptId);
    }

    private static Optional<String> firstGroupOf(String html) {
        Matcher m = RADIO_ID.matcher(html);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static Optional<String> scriptId(String html) {
        Matcher m = SCRIPT_ID.matcher(html);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

}
