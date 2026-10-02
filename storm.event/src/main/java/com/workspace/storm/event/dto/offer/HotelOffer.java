package com.workspace.storm.event.dto.offer;

import java.time.Instant;
import java.util.Map;

public class HotelOffer extends Offer {

    private String roomType;
    private Integer capacity;
    private Integer stars;
    private Boolean breakfastIncluded;
    private String bookingUrl;

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getStars() { return stars; }
    public void setStars(Integer stars) { this.stars = stars; }
    public Boolean getBreakfastIncluded() { return breakfastIncluded; }
    public void setBreakfastIncluded(Boolean breakfastIncluded) { this.breakfastIncluded = breakfastIncluded; }
    public String getBookingUrl() { return bookingUrl; }
    public void setBookingUrl(String bookingUrl) { this.bookingUrl = bookingUrl; }
}