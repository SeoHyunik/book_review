package com.example.macronews;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class MacroNewsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MacroNewsApplication.class, args);
    }
}
