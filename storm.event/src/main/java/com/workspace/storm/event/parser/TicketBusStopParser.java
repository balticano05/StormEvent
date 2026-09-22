package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.tb.TbStop;
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
            TbStop stop = parseRow(row);
            if (stop != null) {
                stops.add(stop);
            }
        }
        return stops;
    }

    private TbStop parseRow(Element row) {
        List<Element> cells = new ArrayList<>();
        for (Element child : row.children()) {
            if ("td".equals(child.tagName())) {
                cells.add(child);
            }
        }
        if (cells.size() < 2) {
            return null;
        }
        TbStop stop = new TbStop();
        stop.setStationId(stationId(row, cells.get(0)));
        stop.setName(text(cell(cells, 1)));
        stop.setDistance(text(cell(cells, 2)));
        stop.setDeparture(text(cell(cells, 3)));
        stop.setArrival(text(cell(cells, 4)));
        stop.setAddress(text(cell(cells, 5)));
        return stop;
    }

    private String stationId(Element row, Element first) {
        if (first != null) {
            Matcher m = RADIO_ID.matcher(first.outerHtml());
            if (m.find()) {
                return m.group(1);
            }
        }
        Matcher ms = SCRIPT_ID.matcher(row.html());
        return ms.find() ? ms.group(1) : null;
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