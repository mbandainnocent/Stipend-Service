package com.stipend.service.Exception;

public class InvalidStipendTransitionException extends RuntimeException {
    public InvalidStipendTransitionException(String message) {
        super(message);
    }
}
