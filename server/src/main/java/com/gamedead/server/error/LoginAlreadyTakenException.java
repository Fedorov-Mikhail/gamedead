package com.gamedead.server.error;
public class LoginAlreadyTakenException extends RuntimeException {
    public LoginAlreadyTakenException() {super("Логин уже занят");}
}
