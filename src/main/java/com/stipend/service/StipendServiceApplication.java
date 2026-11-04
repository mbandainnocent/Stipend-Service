package com.stipend.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StipendServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StipendServiceApplication.class, args);
    }
}
