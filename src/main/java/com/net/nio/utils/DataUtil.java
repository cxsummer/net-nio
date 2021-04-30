package com.net.nio.utils;

import java.util.stream.IntStream;

/**
 * @author caojiancheng
 * @date 2021/4/30
 * @description
 */
public class DataUtil {
    /**
     * byte数组扩容
     *
     * @param origin
     * @return
     */
    public static byte[] byteExpansion(byte[] origin) {
        byte[] temp = new byte[origin.length + 1024];
        IntStream.range(0, origin.length).forEach(i -> temp[i] = origin[i]);
        return temp;
    }
}
