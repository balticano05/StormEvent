package com.workspace.storm.event.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class HotelSearchRequest {

    private Long cityId = 35L;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkIn = LocalDate.now().plusDays(1);
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkOut = LocalDate.now().plusDays(2);
    private int adults = 1;
    private int children = 0;
    private String lang = "ru";

}