package com.workspace.storm.event.parser;

import com.workspace.storm.event.entity.atlas.AtlasRide;
import com.workspace.storm.event.entity.atlas.AtlasSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class AtlasSseParser {

    private static final String EVENT_PROGRESS = "progress";
    private static final String EVENT_RIDES = "rides";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_ERROR = "error";

    private final ObjectMapper mapper = new ObjectMapper();

    public AtlasSearchResult parse(String sseText) {
        if (sseText == null || sseText.isBlank()) {
            throw new IllegalArgumentException("sseText must not be null or blank");
        }

        AtlasSearchResult result = new AtlasSearchResult();

        for (EventBlock block : EventBlock.split(sseText)) {
            switch (block.eventName()) {
                case EVENT_PROGRESS -> handleProgress(result, block.data());
                case EVENT_RIDES -> handleRides(result, block.data());
                case EVENT_DONE -> handleDone(result, block.data());
                case EVENT_ERROR -> result.getErrors().add(block.data().trim());
                default -> log.debug("Ignoring unknown Atlas SSE event '{}'", block.eventName());
            }
        }

        return result;
    }

    private void handleRides(AtlasSearchResult result, String data) {
        readTree(data, "rides").filter(node -> node.hasNonNull("rides")).ifPresent(node -> {
            try {
                List<AtlasRide> rides = mapper.readValue(node.get("rides").toString(),
                        new TypeReference<List<AtlasRide>>() { });
                result.getRides().addAll(rides);
            } catch (JacksonException e) {
                log.debug("Skipping malformed rides payload: {}", e.getMessage());
            }
        });
    }

    private void handleProgress(AtlasSearchResult result, String data) {
        readTree(data, "progress").ifPresent(node -> {
            result.setProgressCompleted(node.path("completed").asInt());
            result.setProgressTotal(node.path("total").asInt());
            result.setProgressReached(true);
        });
    }

    private void handleDone(AtlasSearchResult result, String data) {
        readTree(data, "done").ifPresent(node -> {
            result.setPartial(node.path("partial").asBoolean(false));
            result.setTotalRides(node.path("totalRides").asInt());
            result.setDurationMs(node.path("durationMs").asLong());
            node.path("succeeded").forEach(x -> result.getSucceeded().add(x.asText()));
            node.path("failed").forEach(x -> result.getFailed().add(x.asText()));
        });
    }

    private Optional<JsonNode> readTree(String data, String eventName) {
        if (data == null || data.isBlank()) {
            log.debug("Skipping Atlas SSE event '{}' without data", eventName);
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.readTree(data));
        } catch (JacksonException e) {
            log.debug("Skipping malformed Atlas SSE event '{}': {}", eventName, e.getMessage());
            return Optional.empty();
        }
    }

    private record EventBlock(String eventName, String data) {

        static List<EventBlock> split(String sseText) {
            java.util.List<EventBlock> blocks = new java.util.ArrayList<>();
            for (String raw : sseText.split("\\r?\\n\\s*\\r?\\n")) {
                Optional<String> event = Optional.empty();
                StringBuilder data = new StringBuilder();
                for (String line : raw.split("\\r?\\n")) {
                    if (line.startsWith("event:")) {
                        event = Optional.of(line.substring("event:".length()).trim());
                    } else if (line.startsWith("data:")) {
                        if (!data.isEmpty()) {
                            data.append('\n');
                        }
                        data.append(line.substring("data:".length()).trim());
                    }
                }
                event.ifPresent(name -> blocks.add(new EventBlock(name, data.toString())));
            }
            return blocks;
        }
    }

}