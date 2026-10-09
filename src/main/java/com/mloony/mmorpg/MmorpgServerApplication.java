package com.mloony.mmorpg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.mloony.mmorpg.config.GameProperties;

@SpringBootApplication
@EnableConfigurationProperties(GameProperties.class)
public class MmorpgServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MmorpgServerApplication.class, args);
    }
}
