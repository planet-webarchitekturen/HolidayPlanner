package com.holidayplanner.eventservice.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Manuell in IntelliJ ausführen: Rechtsklick → Run 'run'
 * Läuft gegen die echte DB/Kafka (application.yml).
 * Voraussetzung: Postgres + Kafka müssen laufen.
 */
@SpringBootTest
@Disabled("Manual test: requires real Postgres and Kafka")
class RunAutoCancelManualTest {

    @Autowired
    AutoCancelUnderfilledTermsJob job;

    @Test
    void run() {
        job.autoCancelUnderfilledTerms();
    }
}
