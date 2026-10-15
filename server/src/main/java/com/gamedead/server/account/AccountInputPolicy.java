package com.gamedead.server.account;

import com.gamedead.server.error.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class AccountInputPolicy {
    public String normalizeLogin(String input) {
        if (input == null) throw invalid("login", "Укажи логин");
        String value = input.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_]{3,24}")) throw invalid("login", "Нужно 3–24 латинских буквы, цифры или подчёркивания");
        return value;
    }
    public String normalizeDisplayName(String input) {
        if (input == null) throw invalid("displayName", "Укажи имя");
        if (input.codePoints().anyMatch(Character::isISOControl)) throw invalid("displayName", "Управляющие символы запрещены");
        String value = input.strip();
        int length = value.codePointCount(0, value.length());
        if (length < 3 || length > 24) throw invalid("displayName", "Нужно 3–24 символа");
        return value;
    }
    public void validatePassword(String input) {
        if (input == null || input.codePointCount(0, input.length()) < 8 || input.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalid("password", "Нужно минимум 8 символов и максимум 72 байта UTF-8");
        }
    }
    private InvalidInputException invalid(String field, String message) { return new InvalidInputException(field, message); }
}
