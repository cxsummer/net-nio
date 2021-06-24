package com.net.nio.exception;

/**
 * @author caojiancheng
 * @date 2021/5/8
 * @description
 */
public class AssertionException extends RuntimeException {

    public AssertionException(Throwable cause) {
        super(cause);
    }

    public AssertionException(String message) {
        super(message);
    }
}
