package com.stipend.service;

import com.stipend.service.kafka.config.KafkaRetryConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(KafkaRetryConfig.class)
public class StipendServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StipendServiceApplication.class, args);
    }
}
