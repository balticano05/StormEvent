package com.workspace.storm.event.client;

import com.workspace.storm.event.dto.TicketBusSearchRequest;
import com.workspace.storm.event.entity.tb.TbRace;
import com.workspace.storm.event.entity.tb.TbSchedule;
import com.workspace.storm.event.entity.tb.TbStation;
import com.workspace.storm.event.entity.tb.TbStop;
import com.workspace.storm.event.exception.TicketBusClientException;
import com.workspace.storm.event.parser.TicketBusRaceParser;
import com.workspace.storm.event.parser.TicketBusScheduleParser;
import com.workspace.storm.event.parser.TicketBusStationParser;
import com.workspace.storm.event.parser.TicketBusStopParser;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.workspace.storm.event.utils.Constants.TICKETBUS_BASE_URL;
import static com.workspace.storm.event.utils.Constants.TICKETBUS_DATE;
import static com.workspace.storm.event.utils.Constants.TICKETBUS_USER_AGENT;

@Component
public class TicketBusClient {

    private static final Logger log = LoggerFactory.getLogger(TicketBusClient.class);
    private static final String ACCESS_DENIED = "Нарушен доступ";
    private static final String SESSION_ENDED = "endsessiion()";
    private static final Pattern SESSION_PATTERN = Pattern.compile("PHPSESSID=([A-Za-z0-9]+)");

    private static final int STATION_LIMIT = 1000;
    private static final String HOST_ID = "1";

    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(TICKETBUS_DATE);

    private final TicketBusStationParser stationParser;
    private final TicketBusRaceParser raceParser;
    private final TicketBusScheduleParser scheduleParser;
    private final TicketBusStopParser stopParser;
    private final OkHttpClient http;

    private final AtomicReference<String> sessionId = new AtomicReference<>();
    private volatile boolean warmed;

    public TicketBusClient(OkHttpClient okHttpClient,
                           TicketBusStationParser stationParser,
                           TicketBusRaceParser raceParser,
                           TicketBusScheduleParser scheduleParser,
                           TicketBusStopParser stopParser) {
        this.stationParser = stationParser;
        this.raceParser = raceParser;
        this.scheduleParser = scheduleParser;
        this.stopParser = stopParser;
        this.http = okHttpClient.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public List<TbStation> resolveStations(String query, String originId, boolean fromCity) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }

        HttpUrl url = baseBuilder()
                .addQueryParameter("PHPSESSID", session())
                .addQueryParameter("prog", "station1")
                .addQueryParameter("q", query)
                .addQueryParameter("limit", String.valueOf(STATION_LIMIT))
                .addQueryParameter("timestamp", String.valueOf(System.currentTimeMillis()))
                .addQueryParameter("selectcity", fromCity ? "1" : "0")
                .addQueryParameter("station_id1", originId)
                .addQueryParameter("path", "station")
                .build();

        return stationParser.parse(execute(get(url)));
    }

    public List<TbRace> searchRaces(TicketBusSearchRequest req) {
        if (req == null || req.getFromId() == null || req.getToId() == null || req.getDate() == null) {
            throw new IllegalArgumentException("fromId, toId and date must not be null");
        }
        session();

        HttpUrl url = dataUrl("marshrut1");
        RequestBody body = new FormBody.Builder()
                .add("station_id", req.getToId())
                .add("station_id1", req.getFromId())
                .add("date", dateFormat.format(req.getDate()))
                .add("online", "")
                .add("path", req.isFromCity() ? "1" : "0")
                .add("mparent", "0")
                .add("codest", "")
                .build();

        return raceParser.parse(executePost(url, body));
    }

    public List<TbSchedule> routeSchedule(TicketBusSearchRequest req) {
        if (req == null || req.getFromId() == null || req.getToId() == null) {
            throw new IllegalArgumentException("fromId and toId must not be null");
        }
        session();

        HttpUrl url = dataUrl("raspis2");
        RequestBody body = new FormBody.Builder()
                .add("station_id", req.getToId())
                .add("station_id1", req.getFromId())
                .add("path", req.isFromCity() ? "1" : "0")
                .build();

        return scheduleParser.parse(executePost(url, body));
    }

    public List<TbStop> raceStops(String raceCode) {
        if (raceCode == null || raceCode.isBlank()) {
            throw new IllegalArgumentException("raceCode must not be null or blank");
        }
        session();

        HttpUrl url = dataUrl("getstation1");
        RequestBody body = new FormBody.Builder()
                .add("station_id", raceCode)
                .build();

        return stopParser.parse(executePost(url, body));
    }

    private String executePost(HttpUrl url, RequestBody body) {
        String html = execute(new Request.Builder().url(url).post(body).build());
        if (!isAccessDenied(html)) {
            return html;
        }
        log.warn("TicketBus access denied for {}; renewing session", url.queryParameter("prog"));
        dropSession();
        return execute(new Request.Builder().url(withSession(url.queryParameter("prog"))).post(body).build());
    }

    private void refreshSession() {
        HttpUrl url = dataUrl("getsession1");
        RequestBody body = new FormBody.Builder()
                .add("PHPSESSID", sessionId.get())
                .build();
        String response = execute(new Request.Builder().url(url).post(body).build());
        Matcher m = SESSION_PATTERN.matcher(response);
        if (m.find()) {
            sessionId.set(m.group(1));
        }
    }

    private String session() {
        warmup();
        String sid = sessionId.get();
        if (sid == null || sid.isBlank()) {
            throw new TicketBusClientException("No TicketBus session available");
        }
        return sid;
    }

    private void warmup() {
        if (warmed) {
            return;
        }
        synchronized (this) {
            if (warmed) {
                return;
            }
            String html = execute(get(indexUrl()));
            Matcher m = SESSION_PATTERN.matcher(html);
            if (!m.find()) {
                throw new TicketBusClientException("No PHPSESSID found in TicketBus homepage");
            }
            sessionId.set(m.group(1));
            refreshSession();
            warmed = true;
        }
    }

    private void dropSession() {
        warmed = false;
        sessionId.set(null);
    }

    private HttpUrl dataUrl(String prog) {
        return baseBuilder()
                .addQueryParameter("PHPSESSID", sessionId.get() == null ? "" : sessionId.get())
                .addQueryParameter("prog", prog)
                .addQueryParameter("host", HOST_ID)
                .build();
    }

    private HttpUrl withSession(String prog) {
        session();
        return dataUrl(prog);
    }

    private HttpUrl indexUrl() {
        return Optional.ofNullable(HttpUrl.parse(TICKETBUS_BASE_URL + "/"))
                .orElseThrow(() -> new IllegalStateException("Invalid TicketBus index URL: " + TICKETBUS_BASE_URL));
    }

    private Request get(HttpUrl url) {
        return new Request.Builder().url(url).get().build();
    }

    private String execute(Request request) {
        try (Response response = http.newCall(decorate(request)).execute()) {
            if (!response.isSuccessful()) {
                throw new TicketBusClientException("TicketBus returned HTTP " + response.code() + " for " + request.url());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new TicketBusClientException("Empty TicketBus response for " + request.url());
            }
            return body.string();
        } catch (IOException e) {
            throw new TicketBusClientException("Failed to load " + request.url(), e);
        }
    }

    private Request decorate(Request request) {
        return request.newBuilder()
                .header("User-Agent", TICKETBUS_USER_AGENT)
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .header("Referer", TICKETBUS_BASE_URL + "/")
                .header("X-Requested-With", "XMLHttpRequest")
                .build();
    }

    private boolean isAccessDenied(String html) {
        return html != null && !html.isBlank()
                && (html.contains(ACCESS_DENIED) || html.contains(SESSION_ENDED));
    }

    private HttpUrl.Builder baseBuilder() {
        String url = TICKETBUS_BASE_URL + "/getdata.php";
        return Optional.ofNullable(HttpUrl.parse(url))
                .map(HttpUrl::newBuilder)
                .orElseThrow(() -> new IllegalStateException("Invalid TicketBus base URL: " + url));
    }

}