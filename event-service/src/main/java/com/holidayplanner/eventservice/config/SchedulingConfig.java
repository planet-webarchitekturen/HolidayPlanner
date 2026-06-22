package com.holidayplanner.eventservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class SchedulingConfig {

    /**
     * Pins the schedulers to a fixed zone so "now"/"tomorrow"/"within 24h" do not depend on the
     * JVM default timezone (which varies between local machines and containers). Injecting a Clock
     * also lets the scheduler unit tests use a {@code Clock.fixed(...)} for deterministic assertions.
     */
    @Bean
    public Clock clock(@Value("${event-service.scheduler.zone:Europe/Vienna}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
