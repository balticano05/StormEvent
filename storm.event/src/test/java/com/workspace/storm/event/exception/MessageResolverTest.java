package com.workspace.storm.event.exception;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class MessageResolverTest {

    private final MessageResolver resolver;

    MessageResolverTest() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        this.resolver = new MessageResolver(messageSource);
    }

    @Test
    void resolvesExistingKey() {
        LocaleContextHolder.setLocale(new Locale("ru"));
        try {
            String msg = resolver.resolve(ErrorCode.VALIDATION_ERROR);
            assertEquals("Некорректные данные запроса", msg);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void resolvesWithArgs() {
        LocaleContextHolder.setLocale(new Locale("ru"));
        try {
            String msg = resolver.resolve(ErrorCode.VALIDATION_ERROR, "field", "value");
            assertEquals("Некорректные данные запроса", msg);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void fallsBackToDefaultMessage() {
        LocaleContextHolder.setLocale(new Locale("ru"));
        try {
            String msg = resolver.resolve(ErrorCode.VALIDATION_ERROR, "default");
            assertEquals("Некорректные данные запроса", msg);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void returnsKeyWhenMissingAndNoDefault() {
        ReloadableResourceBundleMessageSource emptySource = new ReloadableResourceBundleMessageSource();
        emptySource.setBasename("classpath:messages");
        emptySource.setDefaultEncoding("UTF-8");
        emptySource.setFallbackToSystemLocale(false);
        MessageResolver emptyResolver = new MessageResolver(emptySource);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        try {
            String msg = emptyResolver.resolve(ErrorCode.VALIDATION_ERROR);
            assertEquals("Invalid request data", msg);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void usesRussianByDefault() {
        LocaleContextHolder.setLocale(new Locale("ru"));
        try {
            String msg = resolver.resolve(ErrorCode.INTERNAL_ERROR);
            assertEquals("Внутренняя ошибка сервера", msg);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void usesEnglishWhenLocaleSet() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        try {
            String msg = resolver.resolve(ErrorCode.INTERNAL_ERROR);
            assertEquals("Internal server error", msg);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}