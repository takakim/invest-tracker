package com.takakim.investtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InvestTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestTrackerApplication.class, args);
    }
}
