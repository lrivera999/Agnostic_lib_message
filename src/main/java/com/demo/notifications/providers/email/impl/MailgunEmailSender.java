package com.demo.notifications.providers.email.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.core.interfaces.NotificationSender;

public final class MailgunEmailSender implements NotificationSender {
    private static final Logger logger = LoggerFactory.getLogger(MailgunEmailSender.class);
    private final String apiKey;
    private final String domain;
    private final String from;

    private static final String PROVIDER_NAME = "Mailgun";

    public MailgunEmailSender(String apiKey, String domain, String from) {
        this.apiKey = apiKey;
        this.domain = domain;
        this.from = from;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public String provider() {
        return PROVIDER_NAME;
    }

    @Override
    public NotificationResult send(NotificationRequest request) throws NotificationException {
        logger.info(
            "Sending email via Mailgun: domain={} from={} to={} subject={} message={}",
            domain,
            from,
            request.recipient(),
            request.subject(),
            request.message());
        return NotificationResult.success(channel(), provider());
    }
}
