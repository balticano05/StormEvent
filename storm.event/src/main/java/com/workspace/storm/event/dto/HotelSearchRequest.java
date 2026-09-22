package com.workspace.storm.event.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class HotelSearchRequest {

    private Long cityId = 35L;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkIn = LocalDate.now().plusDays(1);

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkOut = LocalDate.now().plusDays(2);

    @Min(1)
    private int adults = 1;

    @Min(0)
    private int children = 0;

    @Pattern(regexp = "ru|en", message = "lang must be 'ru' or 'en'")
    private String lang = "ru";

    @AssertTrue(message = "checkOut must be after checkIn")
    public boolean isCheckOutAfterCheckIn() {
        return checkOut == null || checkIn == null || checkOut.isAfter(checkIn);
    }

}