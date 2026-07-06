package com.velocity.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI velocityOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VeloCity API")
                        .description("REST API documentation for the VeloCity e-bike rental platform.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("VeloCity Engineering")
                                .email("dev@velocity.com")));
    }
}