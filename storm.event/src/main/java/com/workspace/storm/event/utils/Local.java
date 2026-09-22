package com.workspace.storm.event.utils;

import lombok.experimental.UtilityClass;

import java.util.Locale;

@UtilityClass
public class Local {

    public static String normalizedLang(String lang) {
        if (lang == null) return "ru";
        String l = lang.toLowerCase(Locale.ROOT);
        return l.startsWith("en") ? "en" : "ru";
    }

}
