package com.takakim.investtracker.service;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
