package com.itasm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ItAssetServiceManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItAssetServiceManagementApplication.class, args);
    }
}
