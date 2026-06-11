package com.campuslens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampusLensBackendApplication {
  public static void main(String[] args) {
    SpringApplication.run(CampusLensBackendApplication.class, args);
  }
}
