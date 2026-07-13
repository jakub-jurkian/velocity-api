package com.velocity.api.conf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Global security configurations for the application.
 * Currently limited to cryptographic beans to avoid premature HTTP lockdown.
 */
@Configuration
public class SecurityConfig {
    // tells Spring to run this method once, take the resulting
    // BCryptPasswordEncoder object, and put it into the application context.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
