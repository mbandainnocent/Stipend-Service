package com.stipend.service.Exception;

public class FinancePrincipalRequiredException extends RuntimeException {
    public FinancePrincipalRequiredException() {
        super("An authenticated finance user is required");
    }
}
