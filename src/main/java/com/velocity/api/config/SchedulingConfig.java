package com.velocity.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SchedulingConfig {
    // The class exists purely as a "switchboard" to hold the annotations.
    // You are telling Spring, "If this condition is met, load this file
    // and turn on the @EnableScheduling engine." It doesn't need any methods or beans inside it.
}
