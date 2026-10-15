package com.gamedead.client.net;

import java.util.Map;

public final class ApiFailure extends RuntimeException {
    private final int status;
    private final Map<String, String> fieldErrors;

    public ApiFailure(int status, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public ApiFailure(int status, String message) { this(status, message, Map.of()); }
    public int status() { return status; }
    public Map<String, String> fieldErrors() { return fieldErrors; }
}
