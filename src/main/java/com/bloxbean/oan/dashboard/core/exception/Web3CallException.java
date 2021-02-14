package com.bloxbean.oan.dashboard.core.exception;

public class Web3CallException extends RuntimeException {
    public Web3CallException(String message) {
        super(message);
    }

    public Web3CallException(String message, Exception e) {
        super(message, e);
    }
}
