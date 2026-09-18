package com.workspace.storm.event.utils;

import lombok.experimental.UtilityClass;

import java.time.format.DateTimeFormatter;

@UtilityClass
public class Constants {

    public static final DateTimeFormatter BELHOTEL_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    public static final String BASE_URL = "https://belhotel.by/";

    public static final String TICKETPRO_BASE_URL = "https://www.ticketpro.by";

}
