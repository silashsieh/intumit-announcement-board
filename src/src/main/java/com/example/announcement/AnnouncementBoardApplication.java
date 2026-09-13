package com.example.announcement;

import java.util.Arrays;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AnnouncementBoardApplication {

  public static void main(String[] args) {
    SpringApplication.run(AnnouncementBoardApplication.class, args);
  }

  @Bean
  public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
    return args -> {

      System.out.println("The beans is running...");

    };
  }
}
