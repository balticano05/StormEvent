package com.workspace.storm.event.normalize.source;

import com.workspace.storm.event.dto.offer.HotelOffer;
import com.workspace.storm.event.dto.offer.OfferDomain;
import com.workspace.storm.event.dto.offer.OfferIdFactory;
import com.workspace.storm.event.dto.offer.OfferNormalizer;
import com.workspace.storm.event.dto.offer.Price;
import com.workspace.storm.event.entity.hotel.Hotel;
import com.workspace.storm.event.entity.hotel.RoomOffer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BelHotelOfferMapper {

    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    private BelHotelOfferMapper() {
    }

    public static List<HotelOffer> map(List<Hotel> hotels) {
        if (hotels == null) {
            return List.of();
        }
        List<HotelOffer> offers = new ArrayList<>();
        for (Hotel hotel : hotels) {
            if (hotel.getOffers() != null) {
                for (RoomOffer roomOffer : hotel.getOffers()) {
                    HotelOffer offer = mapRoomOffer(hotel, roomOffer);
                    if (offer != null && OfferNormalizer.isRequiredFieldPresent(offer)) {
                        offers.add(offer);
                    }
                }
            }
        }
        return offers;
    }

    public static HotelOffer mapRoomOffer(Hotel hotel, RoomOffer roomOffer) {
        if (hotel == null || roomOffer == null) {
            return null;
        }
        HotelOffer offer = new HotelOffer();
        offer.setId(OfferIdFactory.createHotel("belhotel", hotel.getId() + "-" + roomOffer.getRoomType()));
        offer.setDomain(OfferDomain.HOTEL);
        offer.setSource("belhotel");
        offer.setKind("hotel");
        offer.setFrom(hotel.getName());
        offer.setTo(hotel.getName());
        offer.setDepartureAt(Instant.now()); // check-in date would come from query
        offer.setArrivalAt(Instant.now().plusSeconds(86400)); // check-out date would come from query

        Price price = OfferNormalizer.normalizePrice(
                roomOffer.getPrice() != null ? roomOffer.getPrice().toPlainString() + " " + roomOffer.getCurrency() : null,
                "belhotel"
        );
        offer.setPrice(price);

        offer.setSeats(roomOffer.getCapacity());

        offer.setAttributes(Map.of(
                "hotelName", hotel.getName(),
                "hotelType", hotel.getType(),
                "stars", hotel.getStars(),
                "roomType", roomOffer.getRoomType(),
                "breakfastIncluded", roomOffer.isBreakfastIncluded(),
                "partial", false
        ));

        offer.setLink(roomOffer.getBookingUrl());

        offer.setRoomType(roomOffer.getRoomType());
        offer.setCapacity(roomOffer.getCapacity());
        offer.setStars(hotel.getStars());
        offer.setBreakfastIncluded(roomOffer.isBreakfastIncluded());
        offer.setBookingUrl(roomOffer.getBookingUrl());

        return offer;
    }
}