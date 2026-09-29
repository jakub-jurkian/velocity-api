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
    // Off in the test profile (scheduling.enabled=false) so the jobs cannot race integration tests.
}
