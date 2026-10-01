package com.workspace.storm.event.exception;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class MessageResolver {

    private final MessageSource messageSource;

    public MessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String resolve(ErrorCode errorCode, Object... args) {
        String key = errorCode.getI18nKey();
        try {
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return key;
        }
    }

    public String resolve(ErrorCode errorCode, String defaultMessage, Object... args) {
        String key = errorCode.getI18nKey();
        try {
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return defaultMessage;
        }
    }
}