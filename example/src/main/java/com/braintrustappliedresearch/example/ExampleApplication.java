package com.braintrustappliedresearch.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Example Spring Boot application that serves the {@code CustomerRules} DMN model through
 * {@code spring-boot-business-rules}. The model is in {@code src/main/resources/rules/}; nothing here wires it up.
 */
@SpringBootApplication
public class ExampleApplication {

    /**
     * Starts the application.
     *
     * @param args command-line arguments, passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
