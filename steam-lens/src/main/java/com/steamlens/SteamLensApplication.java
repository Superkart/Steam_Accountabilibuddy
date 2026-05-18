package com.steamlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class SteamLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(SteamLensApplication.class, args);
    }

}
