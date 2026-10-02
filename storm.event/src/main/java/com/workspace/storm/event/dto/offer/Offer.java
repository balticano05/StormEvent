package com.workspace.storm.event.dto.offer;

import java.time.Instant;
import java.util.Map;

public class Offer {

    private String id;
    private OfferDomain domain;
    private String source;
    private String kind;
    private String from;
    private String to;
    private Instant departureAt;
    private Instant arrivalAt;
    private Price price;
    private Integer seats;
    private String link;
    private Map<String, Object> attributes;


    public Offer() {
    }

    public Offer(String id, OfferDomain domain, String source, String kind, String from, String to,
                 Instant departureAt, Instant arrivalAt, Price price, Integer seats, String link,
                 Map<String, Object> attributes) {
        this.id = id;
        this.domain = domain;
        this.source = source;
        this.kind = kind;
        this.from = from;
        this.to = to;
        this.departureAt = departureAt;
        this.arrivalAt = arrivalAt;
        this.price = price;
        this.seats = seats;
        this.link = link;
        this.attributes = attributes;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public OfferDomain getDomain() { return domain; }
    public void setDomain(OfferDomain domain) { this.domain = domain; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    public Instant getDepartureAt() { return departureAt; }
    public void setDepartureAt(Instant departureAt) { this.departureAt = departureAt; }
    public Instant getArrivalAt() { return arrivalAt; }
    public void setArrivalAt(Instant arrivalAt) { this.arrivalAt = arrivalAt; }
    public Price getPrice() { return price; }
    public void setPrice(Price price) { this.price = price; }
    public Integer getSeats() { return seats; }
    public void setSeats(Integer seats) { this.seats = seats; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}