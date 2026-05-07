package com.holidayplanner.eventservice.port;

import java.util.List;

/**
 * Outbound port for notification-service (synchronous bulk email).
 */
public interface NotificationPort {

    void sendBulkEmail(List<String> recipients, String subject, String body);
}
