package com.gamedead.protocol.error;
import java.util.Map;
public record ApiError(String code, String message, Map<String, String> fieldErrors) {
    public ApiError { fieldErrors = Map.copyOf(fieldErrors); }
}
