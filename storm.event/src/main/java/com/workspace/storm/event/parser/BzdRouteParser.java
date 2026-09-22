package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.bzd.BzdCar;
import com.workspace.storm.event.entity.bzd.BzdTrain;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class BzdRouteParser {

    private static final String ROW_SELECTOR = "div.sch-table__row[data-train-number]";

    public List<BzdTrain> parse(String html) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }
        Document doc = Jsoup.parse(html);
        List<BzdTrain> trains = new ArrayList<>();
        for (Element row : doc.select(ROW_SELECTOR)) {
            trains.add(parseRow(row));
        }
        return trains;
    }

    private BzdTrain parseRow(Element row) {
        BzdTrain train = new BzdTrain();
        train.setNumber(row.attr("data-train-number"));
        train.setType(rowType(row));
        train.setFromTime(text(row.selectFirst(".train-from-time")));
        train.setFromName(text(row.selectFirst(".train-from-name")));
        train.setToTime(text(row.selectFirst(".train-to-time")));
        train.setToName(text(row.selectFirst(".train-to-name")));
        train.setDuration(text(row.selectFirst(".train-duration-time")));
        train.setCars(parseCars(row));
        return train;
    }

    private List<BzdCar> parseCars(Element row) {
        List<BzdCar> cars = new ArrayList<>();
        for (Element item : row.select(".sch-table__t-item")) {
            BzdCar car = new BzdCar();
            car.setName(text(item.selectFirst(".sch-table__t-name")));
            car.setSeats(parseSeats(item.selectFirst(".sch-table__t-quant span")));
            Element link = item.selectFirst("a.sch-table__t-link");
            car.setPriceByn(parsePrice(link == null ? null : link.attr("data-cost-byn")));
            cars.add(car);
        }
        return cars;
    }

    private String rowType(Element row) {
        String type = row.attr("data-train-type");
        if (!type.isBlank()) {
            return type;
        }
        for (String cls : row.classNames()) {
            if (!cls.equals("sch-table__row") && !cls.startsWith("js-")) {
                return cls;
            }
        }
        return null;
    }

    private Integer parseSeats(Element span) {
        if (span == null || span.text().isBlank()) {
            return null;
        }
        String value = span.text().trim();
        return value.chars().allMatch(Character::isDigit) ? Integer.parseInt(value) : null;
    }

    private BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(Element el) {
        return el == null ? null : el.text().replace('\u00A0', ' ').trim();
    }

}