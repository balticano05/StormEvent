package com.workspace.storm.event.client;

import com.workspace.storm.event.exception.TicketProClientException;
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static com.workspace.storm.event.utils.Constants.TICKETPRO_BASE_URL;

@Component
@RequiredArgsConstructor
public class TicketProClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:149.0) Gecko/20100101 Firefox/149.0";

    private final OkHttpClient okHttpClient;

    public String get(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be null or blank");
        }

        HttpUrl url = Optional.ofNullable(HttpUrl.parse(TICKETPRO_BASE_URL + path))
                .orElseThrow(() -> new IllegalArgumentException("Invalid TicketPro path: " + path));

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new TicketProClientException("TicketPro returned HTTP " + response.code() + " for " + path);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new TicketProClientException("Empty TicketPro response for " + path);
            }
            return body.string();

        } catch (IOException e) {
            throw new TicketProClientException("Failed to load TicketPro page " + path, e);
        }
    }

}