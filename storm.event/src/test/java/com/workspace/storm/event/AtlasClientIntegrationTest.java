package com.workspace.storm.event;

import com.workspace.storm.event.client.AtlasClient;
import com.workspace.storm.event.dto.AtlasSearchRequest;
import com.workspace.storm.event.entity.atlas.AtlasSearchResult;
import com.workspace.storm.event.entity.atlas.AtlasStation;
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
public class AtlasClientIntegrationTest {

    @Autowired
    private AtlasClient atlasClient;

    @Test
    void searchGrodnoToBaranovichi() {
        AtlasSearchResult result = atlasClient.search(validRequest("c627904", "c630429"));

        assertNotNull(result);
        assertNotNull(result.getRides());
        assertFalse(result.getRides().isEmpty(), "Ride list should not be empty for Grodno -> Baranovichi");
        log.info("Found {} rides, partial={}, failed={}", result.getRides().size(), result.isPartial(), result.getFailed());
    }

    @Test
    void suggestStationsMinsk() {
        List<AtlasStation> stations = atlasClient.suggestStations("Мин");

        assertNotNull(stations);
        assertFalse(stations.isEmpty(), "Station list should not be empty for 'Мин'");
    }

    private AtlasSearchRequest validRequest(String fromId, String toId) {
        AtlasSearchRequest request = new AtlasSearchRequest();

        request.setFromId(fromId);
        request.setToId(toId);
        request.setDate(LocalDate.now().plusDays(1));
        request.setPassengers(1);

        return request;
    }
}