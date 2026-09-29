package com.velocity.api.common.exception;

import java.util.SortedSet;

public class InvalidSortException extends RuntimeException {
    public InvalidSortException(String field, SortedSet<String> allowed) {
        super("Cannot sort by '" + field + "'. Allowed sort fields: " + String.join(", ", allowed) + ".");
    }
}
