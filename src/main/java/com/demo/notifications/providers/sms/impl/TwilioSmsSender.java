package com.demo.notifications.providers.sms.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.core.interfaces.NotificationSender;

public final class TwilioSmsSender implements NotificationSender {
    private static final Logger logger = LoggerFactory.getLogger(TwilioSmsSender.class);
    private final String authToken;
    private final String accountSid;
    private final String fromPhoneNumber;
    private static final String PROVIDER_NAME = "Twilio";

    public TwilioSmsSender(String authToken, String accountSid, String fromPhoneNumber) {
        this.authToken = authToken;
        this.accountSid = accountSid;
        this.fromPhoneNumber = fromPhoneNumber;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public String provider() {
        return PROVIDER_NAME;
    }

    @Override
    public NotificationResult send(NotificationRequest request) throws NotificationException {
        logger.info(
            "Sending SMS via Twilio: accountSid={} from={} to={} message={}",
            accountSid,
            fromPhoneNumber,
            request.recipient(),
            request.message());
        return NotificationResult.success(channel(), provider());
    }
}
