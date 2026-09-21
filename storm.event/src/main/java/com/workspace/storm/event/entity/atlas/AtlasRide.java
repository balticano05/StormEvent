package com.workspace.storm.event.entity.atlas;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AtlasRide {

    private String id;
    private String rideId;
    private String name;
    private String routeId;
    private String routeName;
    private String carrier;
    private String carrierID;
    private String connector;
    private String partner;
    private String partnerName;
    private String saasId;
    private String currency;

    private BigDecimal price;
    private BigDecimal onlinePrice;
    private BigDecimal serviceFee;
    private BigDecimal fee;
    private BigDecimal baggagePrice;

    private String departure;
    private String arrival;

    private int freeSeats;
    private List<Integer> freeSeatsCount = new ArrayList<>();
    private String status;
    private String rideType;
    private long distance;
    private int flightPopular;
    private boolean onlineRefund;
    private boolean baggageAllowed;
    private boolean seatingRequired;
    private boolean dynamicMode;

    private String borderCrossings;
    private String comment;
    private String extraDescription;
    private String refundConditions;

    private List<String> benefits = new ArrayList<>();
    private List<String> bookFields = new ArrayList<>();
    private List<String> paymentTypes = new ArrayList<>();
    private List<String> saleTypes = new ArrayList<>();

    @JsonProperty("carrier_phones")
    private List<String> carrierPhones = new ArrayList<>();

    @JsonProperty("valid_before")
    private long validBefore;

    private AtlasEndpoint from;
    private AtlasEndpoint to;

    private List<AtlasStop> pickupStops = new ArrayList<>();
    private List<AtlasStop> dischargeStops = new ArrayList<>();
    private Map<String, List<AtlasStop>> rideStops = new LinkedHashMap<>();

    private AtlasBus bus;
    private AtlasFreighter freighter;
    private AtlasLegal legal;
    private AtlasAnimalPolicy animals;
    private AtlasAnimalPolicy luggage;

    private Map<String, Object> carpoolMeta = new LinkedHashMap<>();
    private Map<String, Object> dynamicConfig = new LinkedHashMap<>();

    @JsonProperty("isHidden")
    private boolean hidden;

    @JsonProperty("isPromoEnabled")
    private boolean promoEnabled;

    @JsonProperty("isAutoCallEnabled")
    private boolean autoCallEnabled;

    private Object autoCallSettings;

    private String salesOpenAt;

    private long ticketLimit;

    private AtlasBookingAvailability bookingAvailability;

}