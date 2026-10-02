package com.workspace.storm.event.dto.offer;

public final class OfferIdFactory {

    private OfferIdFactory() {
    }

    public static String create(String source, String kind, String externalKey) {
        return source + ":" + kind + ":" + externalKey;
    }

    public static String createTransport(String source, String externalKey) {
        return create(source, "transport", externalKey);
    }

    public static String createEvent(String source, String externalKey) {
        return create(source, "event", externalKey);
    }

    public static String createHotel(String source, String externalKey) {
        return create(source, "hotel", externalKey);
    }
}