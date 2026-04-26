package com.margdarshak.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableScheduling
public class MargdarshakAiApplication {

    private static final Logger log = LoggerFactory.getLogger(MargdarshakAiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MargdarshakAiApplication.class, args);
    }

    @Bean
    CommandLineRunner onStartup() {
        return args -> log.info("Namaste! MargDarshak AI is ready to guide you.");
    }
}
