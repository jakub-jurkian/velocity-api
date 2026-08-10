package com.velocity.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class VelocityApiApplication {

	static void main(String[] args) {
		SpringApplication.run(VelocityApiApplication.class, args);
	}

}
