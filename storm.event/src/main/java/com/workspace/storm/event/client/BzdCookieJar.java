package com.workspace.storm.event.client;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BzdCookieJar implements CookieJar {

    private final Map<String, List<Cookie>> store = new ConcurrentHashMap<>();

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        if (cookies != null && !cookies.isEmpty()) {
            store.put(url.host(), cookies);
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        return store.getOrDefault(url.host(), List.of());
    }

}