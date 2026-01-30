package com.achiever;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AchieverApplication {

    public static void main(String[] args) {
        SpringApplication.run(AchieverApplication.class, args);
    }
}
