package com.net.nio.exception;

/**
 * @author caojiancheng
 * @date 2021/5/8
 * @description
 */
public class CallBackException extends RuntimeException {
    
    public CallBackException(Throwable cause) {
        super(cause);
    }

    public CallBackException(String message) {
        super(message);
    }
}
