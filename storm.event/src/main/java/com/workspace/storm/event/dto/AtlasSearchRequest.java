package com.workspace.storm.event.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class AtlasSearchRequest {

    @NotBlank(message = "fromId must not be blank")
    private String fromId;

    @NotBlank(message = "toId must not be blank")
    private String toId;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date = LocalDate.now().plusDays(1);

    @Min(1)
    private int passengers = 1;

    @AssertTrue(message = "date must not be in the past")
    public boolean isDateNotInPast() {
        return date == null || !date.isBefore(LocalDate.now());
    }

}