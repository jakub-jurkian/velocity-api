package com.velocity.api.common;

import org.openapitools.jackson.nullable.JsonNullable;

import java.util.function.UnaryOperator;

/**
 * Helpers for normalizing {@link JsonNullable} fields inside PATCH request records.
 * <p>
 * A record's compact constructor runs before bean validation, so normalizing here means
 * constraints such as {@code @Size} and {@code @Pattern} inspect the cleaned value rather
 * than the raw one. Without it, " a " passes {@code @Size(min = 2)} as five characters and
 * is only rejected later by the entity, which costs the caller the per-field error map.
 */
public final class JsonNullables {

    private JsonNullables() {
    }

    /**
     * Applies {@code fn} to the wrapped value when one is actually present.
     * <p>
     * A {@code null} container becomes {@link JsonNullable#undefined()} so callers can call
     * {@code isPresent()} without a null check. An explicit JSON {@code null} is passed
     * through untouched, leaving the entity to reject it.
     */
    public static JsonNullable<String> map(JsonNullable<String> value, UnaryOperator<String> fn) {
        if (value == null) return JsonNullable.undefined();
        if (!value.isPresent() || value.get() == null) return value;
        return JsonNullable.of(fn.apply(value.get()));
    }

    /**
     * Trims the wrapped value, if there is one.
     */
    public static JsonNullable<String> trimmed(JsonNullable<String> value) {
        return map(value, String::trim);
    }
}
