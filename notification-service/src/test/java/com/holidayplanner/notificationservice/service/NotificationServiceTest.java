package com.holidayplanner.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationServiceTest {

    @Test
    void sendEmailDoesNotSendRealEmailYet() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NotificationService notificationService = new NotificationService(mailSender);

        notificationService.sendEmail("parent@example.test", "Subject", "Body");

        verifyNoInteractions(mailSender);
    }
}
