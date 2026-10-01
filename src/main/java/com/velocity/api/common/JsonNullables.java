package com.velocity.api.common;

import org.openapitools.jackson.nullable.JsonNullable;

import java.util.function.UnaryOperator;


// Helpers for normalizing JsonNullable fields inside PATCH request records.
// A record's compact constructor runs before bean validation, so normalizing here means
// constraints such as @Size and @Pattern inspect the cleaned value rather
// than the raw one. Without it, " a " passes @Size(min = 2) as five characters and
// is only rejected later by the entity, which costs the caller the per-field error map.
public final class JsonNullables {

    private JsonNullables() {
    }

    // Applies fn to the wrapped value when one is actually present.
    // A null container becomes JsonNullable#undefined() so callers can call
    // isPresent() without a null check. An explicit JSON null is passed
    // through untouched, leaving the entity to reject it.
    public static JsonNullable<String> map(JsonNullable<String> value, UnaryOperator<String> fn) {
        if (value == null) return JsonNullable.undefined();
        if (!value.isPresent() || value.get() == null) return value;
        return JsonNullable.of(fn.apply(value.get()));
    }

    // Trims the wrapped value, if there is one
    public static JsonNullable<String> trimmed(JsonNullable<String> value) {
        return map(value, String::trim);
    }
}
