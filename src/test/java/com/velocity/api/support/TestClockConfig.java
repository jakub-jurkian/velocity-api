package com.velocity.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.ZoneId;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    // @Primary, so every Clock injection point - services, auditing, the validator - gets
    // this one instead of ClockConfig's system clock. Same zone as production.
    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(ZoneId.of("Europe/Warsaw"));
    }
}
