package com.workspace.storm.event.client;

import com.workspace.storm.event.dto.HotelSearchRequest;
import com.workspace.storm.event.entity.Hotel;
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
import java.util.Objects;

import static com.workspace.storm.event.utils.Constants.BASE_URL;
import static com.workspace.storm.event.utils.Constants.BELHOTEL_DATE;

@Component
@RequiredArgsConstructor
public class BelHotelClient {

    private final OkHttpClient okHttpClient;
    private final BelHotelResponseParser responseParser;

    public List<Hotel> search(HotelSearchRequest req) {

        String lang = Local.normalizedLang(req.getLang());
        HttpUrl base = Objects.requireNonNull(HttpUrl.parse(BASE_URL));

        HttpUrl.Builder urlBuilder = base.newBuilder();
        if ("en".equals(lang)) {
            urlBuilder.addPathSegment("en");
        }

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL)).newBuilder()
                .addQueryParameter("calendar", "detail")
                .addQueryParameter("city", String.valueOf(req.getCityId()))
                .addQueryParameter("now_date1", req.getCheckIn().format(BELHOTEL_DATE))
                .addQueryParameter("now_date2", req.getCheckOut().format(BELHOTEL_DATE))
                .addQueryParameter("group_adult", String.valueOf(req.getAdults()))
                .addQueryParameter("group_children", String.valueOf(req.getChildren()))
                .build();

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
            try (InputStream in = body.byteStream()) {
                Document doc = Jsoup.parse(in, "windows-1251", url.toString());
                return responseParser.parse(doc, req.getCityId());
            }

        } catch (IOException e) {
            throw new BelHotelClientException("Failed to load Belhotel page", e);
        }
    }

}