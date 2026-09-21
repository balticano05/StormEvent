package com.workspace.storm.event.entity.ticket;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record Category(String name, String slug, List<Category> subcategories) {

    public boolean hasSubcategories() {
        return subcategories != null && !subcategories.isEmpty();
    }

    @NotNull
    @Override
    public String toString() {
        return name + " (" + slug + ")";
    }

}