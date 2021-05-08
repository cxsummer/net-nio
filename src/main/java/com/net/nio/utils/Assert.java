package com.net.nio.utils;

import com.net.nio.exception.AssertionException;

/**
 * @author caojiancheng
 * @date 2021/5/8
 * @description
 */
public class Assert {
    public static void isTrue(boolean test, String message) {
        if (!test) {
            throw new AssertionException(message);
        }
    }
}
