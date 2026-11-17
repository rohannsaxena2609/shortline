package dev.shortline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ShortlineApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShortlineApplication.class, args);
    }
}
