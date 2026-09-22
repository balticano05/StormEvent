package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.atlas.AtlasStation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class AtlasStationParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<AtlasStation> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<AtlasStation>>() { });
        } catch (JacksonException e) {
            log.debug("Skipping malformed station list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

}