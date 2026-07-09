package com.demo.notifications.validations;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.enums.MessageChannel;
import com.demo.notifications.core.exceptions.NotificationException;

public class NotificationValidator {
    
    private static Logger logger = LoggerFactory.getLogger(NotificationValidator.class);
    
    static final String PROVIDER_NAME = "Twilio";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{8,15}$");
    
    private NotificationValidator() {}

    public static void validate(NotificationRequest request) {
        if (request == null) {
            throw new NotificationException(MessageChannel.MSG_REQUEST_NULL);
        }
        if (request.channel() == null) {
            throw new NotificationException(MessageChannel.MSG_CHANNEL_NOT_BLANK);
        }
        if (request.recipient() == null || request.recipient().isBlank()) {
            throw new NotificationException(MessageChannel.MSG_BLANK_RECIPIENT);
        }
        if (request.message() == null || request.message().isBlank()) {
            throw new NotificationException(MessageChannel.MSG_BLANK_MESSAGE);
        }

        switch (request.channel()) {
            case EMAIL -> {
                validateEmail(request.recipient());
                validateSubject(request.subject());
            }
            case SMS -> validatePhone(request.recipient());
            case PUSH -> {
                validatePushToken(request.recipient());
                validateTitle(request.title());
            }
            default -> throw new NotificationException(MessageChannel.MSG_CHANNEL_NOT_SUPPORTED);
        }
    }

    private static void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new NotificationException(MessageChannel.MSG_INVALID_EMAIL + email);
        }
    }

    private static void validatePhone(String phone) {
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new NotificationException(MessageChannel.MSG_INVALID_PHONE + phone);
        }
    }

    private static void validatePushToken(String token) {
        if (token.length() < 10) {
            throw new NotificationException(MessageChannel.MSG_INVALID_PUSH_TOKEN);
        }
    }

    private static void validateSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new NotificationException(MessageChannel.MSG_BLANK_SUBJECT);
        }
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new NotificationException(MessageChannel.MSG_BLANK_TITLE);
        }
    }



}
