package com.workspace.storm.event.service;

import com.workspace.storm.event.client.AtlasClient;
import com.workspace.storm.event.dto.AtlasSearchRequest;
import com.workspace.storm.event.entity.atlas.AtlasSearchResult;
import com.workspace.storm.event.entity.atlas.AtlasStation;
import com.workspace.storm.event.exception.AtlasServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AtlasService {

    private final AtlasClient client;

    public AtlasSearchResult search(AtlasSearchRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("req must not be null");
        }
        try {
            return client.search(req);
        } catch (RuntimeException e) {
            throw new AtlasServiceException("Failed to search Atlas rides", e);
        }
    }

    public List<AtlasStation> findStations(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("userInput must not be null or blank");
        }
        return client.suggestStations(userInput);
    }

}