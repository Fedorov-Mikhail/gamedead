package com.gamedead.server.error;
import java.util.Map;
public class InvalidInputException extends IllegalArgumentException {
    private final Map<String, String> fields;
    public InvalidInputException(String field, String message) {
        super("Проверь введённые данные");
        this.fields = Map.of(field, message);
    }
    public Map<String, String> fields() { return fields; }
}
