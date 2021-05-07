package com.net.nio.utils;

import java.util.Arrays;
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

    /**
     * byte数组合并
     *
     * @param data1 要合并的数组1
     * @param data2 要合并的数组2
     * @return
     */
    public static byte[] byteConcat(byte[] data1, byte[] data2) {
        byte[] data3 = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data3, 0, data1.length);
        System.arraycopy(data2, 0, data3, data1.length, data2.length);
        return data3;
    }
}
