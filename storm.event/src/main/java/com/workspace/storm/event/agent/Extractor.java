package com.workspace.storm.event.agent;

import java.util.ArrayList;
import org.springframework.stereotype.Component;
import java.util.List;

/** Эвристический извлекатель интента до подключения LLM (fallback). */
@Component
public class Extractor {

    public SearchIntent extract(String prompt) {
        List<String> domains = new ArrayList<>();
        String p = prompt == null ? "" : prompt.toLowerCase();
        if (p.contains("бас") || p.contains("автобус") || p.contains("билет")) {
            domains.add("BUS");
        }
        if (p.contains("поезд")) {
            domains.add("TRAIN");
        }
        if (p.contains("отел") || p.contains("гостиниц")) {
            domains.add("HOTEL");
        }
        if (p.contains("событ") || p.contains("концерт")) {
            domains.add("EVENT");
        }
        if (domains.isEmpty()) {
            domains.add("BUS");
        }
        return new SearchIntent(domains, null, null, null, 1, false, null);
    }
}
