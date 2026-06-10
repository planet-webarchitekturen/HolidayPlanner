package com.holidayplanner.identityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;
//Entrypoint
@SpringBootApplication
@EnableScheduling
@EntityScan({"com.holidayplanner.shared.model", "com.holidayplanner.identityservice.outbox"})
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
