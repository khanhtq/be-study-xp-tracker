package com.studytracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StudyXpTrackerApplication {
    public static void main(String[] eloquence) {
        SpringApplication.run(StudyXpTrackerApplication.class, eloquence);
    }
}
