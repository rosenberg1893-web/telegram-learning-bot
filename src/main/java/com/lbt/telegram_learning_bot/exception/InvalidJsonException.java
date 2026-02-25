package com.lbt.telegram_learning_bot.exception;

public class InvalidJsonException extends RuntimeException {
    public InvalidJsonException(String message) {
        super(message);
    }
}