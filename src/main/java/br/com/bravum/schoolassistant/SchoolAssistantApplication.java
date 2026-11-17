package br.com.bravum.schoolassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SchoolAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchoolAssistantApplication.class, args);
    }
}
