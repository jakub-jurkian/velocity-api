package com.velocity.api.pricing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "pricing")
@Validated
public record PricingProperties(
        @NotNull @PositiveOrZero BigDecimal dailyRate
) {
}