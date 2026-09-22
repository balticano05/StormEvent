package com.workspace.storm.event.client;

import com.workspace.storm.event.dto.HotelSearchRequest;
import com.workspace.storm.event.entity.hotel.Hotel;
import com.workspace.storm.event.exception.BelHotelClientException;
import com.workspace.storm.event.parser.BelHotelResponseParser;
import com.workspace.storm.event.utils.Local;
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static com.workspace.storm.event.utils.Constants.BASE_URL;
import static com.workspace.storm.event.utils.Constants.BELHOTEL_DATE;

@Component
@RequiredArgsConstructor
public class BelHotelClient {

    private final OkHttpClient okHttpClient;
    private final BelHotelResponseParser responseParser;

    public List<Hotel> search(HotelSearchRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("req must not be null");
        }

        HttpUrl url = buildUrl(req);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new BelHotelClientException("Belhotel returned HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new BelHotelClientException("Empty Belhotel response");
            }
            try (InputStream in = body.byteStream()) {
                Document doc = Jsoup.parse(in, "windows-1251", url.toString());
                return responseParser.parse(doc, req.getCityId());
            }

        } catch (IOException e) {
            throw new BelHotelClientException("Failed to load Belhotel page", e);
        }
    }

    private HttpUrl buildUrl(HotelSearchRequest req) {
        if (req.getCheckIn() == null || req.getCheckOut() == null) {
            throw new IllegalArgumentException("checkIn and checkOut must not be null");
        }
        Long cityId = req.getCityId();
        if (cityId == null) {
            throw new IllegalArgumentException("cityId must not be null");
        }

        HttpUrl.Builder urlBuilder = Optional.ofNullable(HttpUrl.parse(BASE_URL))
                .map(HttpUrl::newBuilder)
                .orElseThrow(() -> new IllegalStateException("Invalid base URL: " + BASE_URL));

        if ("en".equals(Local.normalizedLang(req.getLang()))) {
            urlBuilder.addPathSegment("en");
        }

        return urlBuilder
                .addQueryParameter("calendar", "detail")
                .addQueryParameter("city", String.valueOf(cityId))
                .addQueryParameter("now_date1", req.getCheckIn().format(BELHOTEL_DATE))
                .addQueryParameter("now_date2", req.getCheckOut().format(BELHOTEL_DATE))
                .addQueryParameter("group_adult", String.valueOf(req.getAdults()))
                .addQueryParameter("group_children", String.valueOf(req.getChildren()))
                .build();
    }

}