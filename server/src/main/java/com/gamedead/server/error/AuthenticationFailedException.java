package com.gamedead.server.error;
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {super("Неверные учётные данные или недействительная сессия");}
}
