package com.demo.notifications.core.interfaces;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;

public interface NotificationSender {
    NotificationChannel channel();
    String provider();
    NotificationResult send(NotificationRequest request) throws NotificationException;
}
