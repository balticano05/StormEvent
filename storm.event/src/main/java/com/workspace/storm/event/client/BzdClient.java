package com.workspace.storm.event.client;

import com.workspace.storm.event.entity.bzd.BzdStation;
import com.workspace.storm.event.entity.bzd.BzdTrain;
import com.workspace.storm.event.exception.BzdClientException;
import com.workspace.storm.event.parser.BzdRouteParser;
import com.workspace.storm.event.parser.BzdStationParser;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.workspace.storm.event.utils.Constants.BZD_BASE_URL;
import static com.workspace.storm.event.utils.Constants.BZD_LOCALE;
import static com.workspace.storm.event.utils.Constants.BZD_USER_AGENT;

@Component
public class BzdClient {

    private static final String[] RU_SHORT_MONTHS =
            {"янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"};

    private final BzdStationParser stationParser;
    private final BzdRouteParser routeParser;
    private final OkHttpClient http;

    private volatile boolean warmed;

    public BzdClient(OkHttpClient okHttpClient,
                     BzdStationParser stationParser,
                     BzdRouteParser routeParser) {
        this.stationParser = stationParser;
        this.routeParser = routeParser;
        this.http = okHttpClient.newBuilder()
                .cookieJar(new BzdCookieJar())
                .build();
    }

    public List<BzdStation> resolveStations(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }
        warmup();

        HttpUrl url = baseBuilder()
                .addPathSegments("ru/ajax/autocomplete/search/")
                .addQueryParameter("term", query)
                .build();

        return stationParser.parse(execute(url));
    }

    public List<BzdTrain> searchRoute(String from, String to, LocalDate date) {
        if (from == null || to == null || date == null) {
            throw new IllegalArgumentException("from, to and date must not be null");
        }
        warmup();

        BzdStation fromStation = resolveStation(from);
        BzdStation toStation = resolveStation(to);

        HttpUrl url = baseBuilder()
                .addPathSegments("ru/route/")
                .addQueryParameter("from", fromStation.getValue())
                .addQueryParameter("from_exp", fromStation.getExp())
                .addQueryParameter("from_esr", fromStation.getEcp())
                .addQueryParameter("to", toStation.getValue())
                .addQueryParameter("to_exp", toStation.getExp())
                .addQueryParameter("to_esr", toStation.getEcp())
                .addQueryParameter("date", date.toString())
                .addQueryParameter("front_date", frontDate(date))
                .build();

        return routeParser.parse(execute(url));
    }

    private BzdStation resolveStation(String query) {
        return resolveStations(query).stream()
                .filter(s -> isCode(s.getExp()) && isCode(s.getEcp()))
                .findFirst()
                .orElseThrow(() -> new BzdClientException("Station not found for query: " + query));
    }

    private boolean isCode(String code) {
        return code != null && !code.isBlank();
    }

    private void warmup() {
        if (warmed) {
            return;
        }
        HttpUrl url = baseBuilder().addPathSegments("ru/").build();
        try (Response response = http.newCall(buildRequest(url)).execute()) {
            if (!response.isSuccessful()) {
                throw new BzdClientException("BZD warmup returned HTTP " + response.code());
            }
        } catch (IOException e) {
            throw new BzdClientException("Failed to warm up BZD session", e);
        }
        warmed = true;
    }

    private String execute(HttpUrl url) {
        try (Response response = http.newCall(buildRequest(url)).execute()) {
            if (!response.isSuccessful()) {
                throw new BzdClientException("BZD returned HTTP " + response.code() + " for " + url);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new BzdClientException("Empty BZD response for " + url);
            }
            return body.string();
        } catch (IOException e) {
            throw new BzdClientException("Failed to load " + url, e);
        }
    }

    private Request buildRequest(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", BZD_USER_AGENT)
                .header("Accept-Language", BZD_LOCALE)
                .header("Referer", BZD_BASE_URL + "/ru/route/")
                .header("X-Requested-With", "XMLHttpRequest")
                .build();
    }

    private HttpUrl.Builder baseBuilder() {
        return Optional.ofNullable(HttpUrl.parse(BZD_BASE_URL))
                .map(HttpUrl::newBuilder)
                .orElseThrow(() -> new IllegalStateException("Invalid BZD base URL: " + BZD_BASE_URL));
    }

    private String frontDate(LocalDate date) {
        return date.getDayOfMonth() + " " + RU_SHORT_MONTHS[date.getMonthValue() - 1] + " " + date.getYear();
    }

}