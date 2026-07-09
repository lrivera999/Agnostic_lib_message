package com.demo.notifications.providers.push.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.core.interfaces.NotificationSender;

public class FirebasePushSender implements NotificationSender 
{
    private static Logger logger = LoggerFactory.getLogger(FirebasePushSender.class);
    private final String apiKey;
    private final String projectId;
    private final String serviceAccount;
    static final String PROVIDER_NAME = "Firebase";

    public FirebasePushSender(String apiKey, String projectId, String serviceAccount) {
        this.apiKey = apiKey;
        this.projectId = projectId;
        this.serviceAccount = serviceAccount;
    }

    @Override
    public NotificationChannel channel(){ return NotificationChannel.PUSH; }

    @Override
    public String provider(){ return PROVIDER_NAME; }

    @Override
    public NotificationResult send(NotificationRequest request) throws NotificationException {
        logger.info(
            "Sending push notification via Firebase: token={} projectId={} title={} message={}",
            request.recipient(),
            projectId,
            request.title(),
            request.message());
        return NotificationResult.success(channel(), provider());
    }
    
}
