package com.gamedead.server;

import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableScheduling
public class GameDeadServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameDeadServerApplication.class, args);
    }
}
