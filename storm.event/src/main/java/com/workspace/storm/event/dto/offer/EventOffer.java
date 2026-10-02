package com.workspace.storm.event.dto.offer;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class EventOffer extends Offer {

    private String venue;
    private String category;
    private List<String> images;
    private Instant startDate;

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
}