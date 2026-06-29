package com.holidayplanner.notificationservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("NotificationService is running!");
  }
}
