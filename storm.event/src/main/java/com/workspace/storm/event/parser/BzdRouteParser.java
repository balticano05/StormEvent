package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.bzd.BzdCar;
import com.workspace.storm.event.entity.bzd.BzdTrain;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
        train.setNumber(Parsers.attr(row, "data-train-number").orElse(null));
        train.setType(rowType(row).orElse(null));
        train.setFromTime(selectText(row, ".train-from-time").orElse(null));
        train.setFromName(selectText(row, ".train-from-name").orElse(null));
        train.setToTime(selectText(row, ".train-to-time").orElse(null));
        train.setToName(selectText(row, ".train-to-name").orElse(null));
        train.setDuration(selectText(row, ".train-duration-time").orElse(null));
        train.setCars(parseCars(row));
        return train;
    }

    private List<BzdCar> parseCars(Element row) {
        List<BzdCar> cars = new ArrayList<>();
        for (Element item : row.select(".sch-table__t-item")) {
            BzdCar car = new BzdCar();
            car.setName(selectText(item, ".sch-table__t-name").orElse(null));
            car.setSeats(seats(item).orElse(null));
            car.setPriceByn(priceByn(item).orElse(null));
            cars.add(car);
        }
        return cars;
    }

    private Optional<String> rowType(Element row) {
        Optional<String> declared = Parsers.attr(row, "data-train-type");
        if (declared.isPresent()) {
            return declared;
        }
        for (String cls : row.classNames()) {
            if (!cls.equals("sch-table__row") && !cls.startsWith("js-")) {
                return Parsers.clean(cls);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> seats(Element item) {
        return Optional.ofNullable(item.selectFirst(".sch-table__t-quant span"))
                .flatMap(Parsers::text)
                .flatMap(Parsers::toInt);
    }

    private static Optional<java.math.BigDecimal> priceByn(Element item) {
        return Optional.ofNullable(item.selectFirst("a.sch-table__t-link"))
                .flatMap(link -> Parsers.attr(link, "data-cost-byn"))
                .flatMap(Parsers::toDecimal);
    }

    private static Optional<String> selectText(Element parent, String selector) {
        return Optional.ofNullable(parent.selectFirst(selector)).flatMap(Parsers::text);
    }

}
