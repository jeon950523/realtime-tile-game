package com.realtimetilegame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RealtimeTileGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeTileGameApplication.class, args);
    }
}
