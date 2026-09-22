package com.workspace.storm.event.client;

import com.workspace.storm.event.dto.AtlasSearchRequest;
import com.workspace.storm.event.entity.atlas.AtlasSearchResult;
import com.workspace.storm.event.entity.atlas.AtlasStation;
import com.workspace.storm.event.exception.AtlasClientException;
import com.workspace.storm.event.parser.AtlasSseParser;
import com.workspace.storm.event.parser.AtlasStationParser;
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.workspace.storm.event.utils.Constants.ATLAS_APPLICATION_VERSION;
import static com.workspace.storm.event.utils.Constants.ATLAS_BASE_URL;
import static com.workspace.storm.event.utils.Constants.ATLAS_LOCALE;
import static com.workspace.storm.event.utils.Constants.ATLAS_SAAS_PARTNER_ID;

@Component
@RequiredArgsConstructor
public class AtlasClient {

    private static final String APP_JSON_ACCEPT = "application/json, text/plain, */*";
    private static final String SSE_ACCEPT = "text/event-stream";

    private final OkHttpClient okHttpClient;
    private final AtlasSseParser sseParser;
    private final AtlasStationParser stationParser;

    public AtlasSearchResult search(AtlasSearchRequest req) {
        if (req == null || req.getFromId() == null || req.getToId() == null || req.getDate() == null) {
            throw new IllegalArgumentException("fromId, toId and date must not be null");
        }

        HttpUrl url = baseBuilder()
                .addPathSegments("api/search/stream")
                .addQueryParameter("from_id", req.getFromId())
                .addQueryParameter("to_id", req.getToId())
                .addQueryParameter("date", req.getDate().toString())
                .addQueryParameter("passengers", String.valueOf(req.getPassengers()))
                .addQueryParameter("source", "web")
                .build();

        Request request = buildRequest(url, SSE_ACCEPT);

        try (Response response = okHttpClient.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new AtlasClientException("Atlas returned HTTP " + response.code() + " for search stream");
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new AtlasClientException("Empty Atlas search stream response");
            }
            return sseParser.parse(body.string());

        } catch (IOException e) {
            throw new AtlasClientException("Failed to load Atlas search stream", e);
        }
    }

    public List<AtlasStation> suggestStations(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("userInput must not be null or blank");
        }

        HttpUrl url = baseBuilder()
                .addPathSegments("api/search/suggest")
                .addQueryParameter("user_input", userInput)
                .addQueryParameter("from_id", "")
                .addQueryParameter("to_id", "")
                .addQueryParameter("locale", ATLAS_LOCALE)
                .build();

        Request request = buildRequest(url, APP_JSON_ACCEPT);

        try (Response response = okHttpClient.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new AtlasClientException("Atlas returned HTTP " + response.code() + " for suggest");
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new AtlasClientException("Empty Atlas suggest response");
            }
            return stationParser.parse(body.string());

        } catch (IOException e) {
            throw new AtlasClientException("Failed to load Atlas stations", e);
        }
    }

    private HttpUrl.Builder baseBuilder() {
        return Optional.ofNullable(HttpUrl.parse(ATLAS_BASE_URL))
                .map(HttpUrl::newBuilder)
                .orElseThrow(() -> new IllegalStateException("Invalid Atlas base URL: " + ATLAS_BASE_URL));
    }

    private Request buildRequest(HttpUrl url, String accept) {
        return new Request.Builder()
                .url(url)
                .get()
                .header("Accept", accept)
                .header("Accept-Language", ATLAS_LOCALE)
                .header("X-Application-Source", "web")
                .header("X-Application-Version", ATLAS_APPLICATION_VERSION)
                .header("X-SAAS-Partner-Id", ATLAS_SAAS_PARTNER_ID)
                .header("Cookie", "next-i18next=" + ATLAS_LOCALE)
                .header("Referer", ATLAS_BASE_URL + "/")
                .build();
    }

}