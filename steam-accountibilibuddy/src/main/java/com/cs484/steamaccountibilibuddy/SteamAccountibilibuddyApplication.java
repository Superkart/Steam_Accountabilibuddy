package com.cs484.steamaccountibilibuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class SteamAccountibilibuddyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SteamAccountibilibuddyApplication.class, args);
    }

}
