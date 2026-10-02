package com.workspace.storm.event.dto.offer;

import java.time.Instant;
import java.util.Map;

public class TransportOffer extends Offer {

    private String busType;
    private String carrier;
    private String trainNumber;
    private String transportClass;

    public String getBusType() { return busType; }
    public void setBusType(String busType) { this.busType = busType; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }
    public String getTransportClass() { return transportClass; }
    public void setTransportClass(String transportClass) { this.transportClass = transportClass; }
}