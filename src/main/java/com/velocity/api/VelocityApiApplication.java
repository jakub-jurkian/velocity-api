package com.velocity.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
@ConfigurationPropertiesScan
public class VelocityApiApplication {

	static void main(String[] args) {
		SpringApplication.run(VelocityApiApplication.class, args);
	}

}
