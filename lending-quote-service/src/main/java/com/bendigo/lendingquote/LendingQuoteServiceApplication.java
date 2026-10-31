package com.bendigo.lendingquote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LendingQuoteServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LendingQuoteServiceApplication.class, args);
    }
}
