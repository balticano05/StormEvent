package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.bzd.BzdStation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class BzdStationParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<BzdStation> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<BzdStation>>() { });
        } catch (JacksonException e) {
            log.debug("Skipping malformed BZD station list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

}