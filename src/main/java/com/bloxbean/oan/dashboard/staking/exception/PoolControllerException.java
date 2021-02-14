package com.bloxbean.oan.dashboard.staking.exception;

public class PoolControllerException extends RuntimeException {
    public PoolControllerException(String message) {
        super(message);
    }

    public PoolControllerException(String message, Exception e) {
        super(message, e);
    }
}
