package com.workspace.storm.event;

import com.workspace.storm.event.client.BelHotelClient;
import com.workspace.storm.event.dto.HotelSearchRequest;
import com.workspace.storm.event.entity.hotel.Hotel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class BelHotelClientIntegrationTest {

    @Autowired
    private BelHotelClient belHotelClient;

    @Test
    void searchMinskOneAdult() {
        List<Hotel> hotels = search(35L, 1, 0);

        assertNotNull(hotels);
        assertFalse(hotels.isEmpty(), "Hotel list should not be empty for Minsk, 1 adult");
    }

    @Test
    void searchMinskTwoAdults() {
        List<Hotel> hotels = search(35L, 2, 0);

        assertNotNull(hotels);
        assertFalse(hotels.isEmpty(), "Hotel list should not be empty for Minsk, 2 adults");
    }

    @Test
    void searchMinskWithChild() {
        List<Hotel> hotels = search(35L, 2, 1);

        assertNotNull(hotels);
        assertFalse(hotels.isEmpty(), "Hotel list should not be empty for Minsk, 2 adults + 1 child");
    }

    @Test
    void searchBrest() {
        List<Hotel> hotels = search(13L, 1, 0);

        assertNotNull(hotels);
        assertFalse(hotels.isEmpty(), "Hotel list should not be empty for Brest, 1 adult");
    }

    @Test
    void searchGrodno() {
        List<Hotel> hotels = search(18L, 1, 0);

        assertNotNull(hotels);
        assertFalse(hotels.isEmpty(), "Hotel list should not be empty for Grodno, 1 adult");
    }

    @Test
    void searchGomel() {
        List<Hotel> hotels = search(100L, 1, 0);

        assertNotNull(hotels);
        assertFalse(hotels.isEmpty(), "Hotel list should not be empty for Gomel, 1 adult");
    }

    private List<Hotel> search(Long cityId, int adults, int children) {
        return belHotelClient.search(validRequest(cityId, adults, children));
    }

    private HotelSearchRequest validRequest(Long cityId, int adults, int children) {
        HotelSearchRequest request = new HotelSearchRequest();

        request.setCityId(cityId);
        request.setCheckIn(LocalDate.now().plusDays(1));
        request.setCheckOut(LocalDate.now().plusDays(2));
        request.setAdults(adults);
        request.setChildren(children);
        request.setLang("en");

        return request;
    }
}