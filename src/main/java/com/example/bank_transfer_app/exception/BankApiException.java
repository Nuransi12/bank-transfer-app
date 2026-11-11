package com.example.bank_transfer_app.exception;
public class BankApiException extends RuntimeException {
    public BankApiException(String message) {
        super(message);
    }
}