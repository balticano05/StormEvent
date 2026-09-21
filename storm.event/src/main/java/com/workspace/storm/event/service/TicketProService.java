package com.workspace.storm.event.service;

import com.workspace.storm.event.client.TicketProClient;
import com.workspace.storm.event.entity.ticket.Category;
import com.workspace.storm.event.entity.ticket.Event;
import com.workspace.storm.event.exception.TicketProClientException;
import com.workspace.storm.event.exception.TicketProServiceException;
import com.workspace.storm.event.parser.TicketProEventParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TicketProService {

    private static final int MAX_PAGES_PER_CATEGORY = 200;
    private static final long PAGE_DELAY_MS = 300;

    private final TicketProClient client;
    private final TicketProEventParser eventParser;

    public List<Event> getAll() {
        List<Event> events = new ArrayList<>();

        for (Category category : categories()) {
            if (category.hasSubcategories()) {
                for (Category sub : category.subcategories()) {
                    collectFromCategory(events, category.name() + " / " + sub.name(), sub.slug());
                }
            } else {
                collectFromCategory(events, category.name(), category.slug());
            }
        }
        return events;
    }

    public List<Event> collectEvents(String categoryPath, String slug) {
        if (categoryPath == null || slug == null) {
            throw new IllegalArgumentException("categoryPath and slug must not be null");
        }
        List<Event> events = new ArrayList<>();
        collectFromCategory(events, categoryPath, slug);
        return events;
    }

    private void collectFromCategory(List<Event> sink, String categoryPath, String slug) {
        int page = 1;
        try {
            while (page <= MAX_PAGES_PER_CATEGORY) {
                String path = slug + (page == 1 ? "" : "?page=" + page);
                String html = client.get(path);

                for (Event e : eventParser.parseEvents(html)) {
                    e.setCategoryPath(categoryPath);
                    sink.add(e);
                }

                if (!eventParser.hasNextPage(html)) {
                    break;
                }
                page++;
                Thread.sleep(PAGE_DELAY_MS);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new TicketProServiceException("Interrupted while collecting category " + slug, ie);
        } catch (TicketProClientException e) {
            throw new TicketProServiceException("Failed to collect category " + slug, e);
        }
    }

    private static List<Category> categories() {
        return List.of(
                new Category("Sport", "/bilety-na-sportivnye-meropriyatiya/", List.of(
                        new Category("Hockey", "/bilety-na-sportivnye-meropriyatiya/bilety-na-xokkej/", List.of()),
                        new Category("Football", "/bilety-na-sportivnye-meropriyatiya/bilety-na-futbol/", List.of()),
                        new Category("Tennis", "/bilety-na-sportivnye-meropriyatiya/bilety-na-tennis/", List.of()),
                        new Category("Basketball", "/bilety-na-sportivnye-meropriyatiya/bilety-na-basketbol/", List.of()),
                        new Category("Volleyball", "/bilety-na-sportivnye-meropriyatiya/bilety-na-volejbol/", List.of()),
                        new Category("Motorsport", "/bilety-na-sportivnye-meropriyatiya/avtosport/", List.of()),
                        new Category("Handball", "/bilety-na-sportivnye-meropriyatiya/bilety-na-gandbol/", List.of()),
                        new Category("Martial Arts", "/bilety-na-sportivnye-meropriyatiya/bilety-na-edinoborstva/", List.of()),
                        new Category("Biathlon", "/bilety-na-sportivnye-meropriyatiya/bilety-na-biatlon/", List.of()))),
                new Category("Circus", "/bilety-na-shou/cirk/", List.of()),
                new Category("Concerts", "/bilety-na-koncert/", List.of(
                        new Category("Rap", "/bilety-na-koncert/rep/", List.of()),
                        new Category("Pop", "/bilety-na-koncert/pop/", List.of()),
                        new Category("Folk", "/bilety-na-koncert/narodnaya/", List.of()),
                        new Category("Jazz", "/bilety-na-koncert/dzhaz/", List.of()),
                        new Category("Classical", "/bilety-na-koncert/klassika/", List.of()),
                        new Category("Chanson", "/bilety-na-koncert/shanson/", List.of()),
                        new Category("Rock", "/bilety-na-koncert/rok/", List.of()),
                        new Category("Orchestra", "/bilety-na-koncert/orkestr/", List.of()),
                        new Category("Imperial Orchestra", "/bilety-na-koncert/imperial-orchestra/", List.of()))),
                new Category("Festivals", "/festivali/", List.of(
                        new Category("Dushevnyj Festival", "/festivali/dushevnyj-festival-1/", List.of()))),
                new Category("Theater", "/bilety-v-teatr/", List.of(
                        new Category("Tours", "/bilety-v-teatr/gastroli/", List.of()),
                        new Category("Drama", "/bilety-v-teatr/drama/", List.of()),
                        new Category("Comedy", "/bilety-v-teatr/komediya/", List.of()),
                        new Category("Ballet", "/bilety-v-teatr/tanec-balet/", List.of()),
                        new Category("Opera", "/bilety-v-teatr/opera/", List.of()),
                        new Category("Musical", "/bilety-v-teatr/myuzikl/", List.of()),
                        new Category("Puppet", "/bilety-v-teatr/kukolnyj/", List.of()),
                        new Category("Children's Performances", "/bilety-v-teatr/detskie/", List.of()))),
                new Category("For Children", "/detskie-meropriyatiya/", List.of(
                        new Category("Children's Performances", "/bilety-v-teatr/detskie/", List.of()),
                        new Category("Circus", "/bilety-na-shou/cirk/", List.of()),
                        new Category("Dolphinarium", "/raznoe/bilety-v-delfinarij/", List.of()),
                        new Category("Zoo", "/raznoe/bilety-v-zoopark/", List.of()))),
                new Category("Misc", "/raznoe/", List.of(
                        new Category("KVN", "/bilety-na-shou/kvn/", List.of()),
                        new Category("Humor", "/bilety-na-shou/yumor/", List.of()),
                        new Category("Show", "/bilety-na-shou/", List.of()),
                        new Category("Circus", "/bilety-na-shou/cirk/", List.of()),
                        new Category("Zoo", "/raznoe/bilety-v-zoopark/", List.of()),
                        new Category("Dolphinarium", "/raznoe/bilety-v-delfinarij/", List.of()),
                        new Category("Exhibitions, Museums", "/raznoe/bilety-na-vystavki-i-muzei/", List.of()),
                        new Category("Education", "/obuchenie/", List.of()),
                        new Category("Trainings", "/raznoe/bilety-na-treningi/", List.of()),
                        new Category("Seminars", "/raznoe/bilety-na-seminary/", List.of()))),
                new Category("Cinema", "/raznoe/bilety-v-kino/", List.of()));
    }

}