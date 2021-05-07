package com.net.nio.model;

import lombok.Data;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author caojiancheng
 * @date 2021/4/26
 * @description
 */
@Data
public class HttpRequestVO {

    /**
     * 请求端口
     */
    private Integer port;

    /**
     * 请求地址
     */
    private String address;

    /**
     * 请求方式
     */
    private String method;

    /**
     * 请求参数字符串
     */
    private String path;

    /**
     * 请求头
     */
    private LinkedHashMap headers;

    /**
     * 请求体
     */
    private byte[] body;

    /**
     * 回调方法
     */
    Consumer<HttpResponseVO> consumer;

    /**
     * 写入的ByteBuffer
     */
    private ByteBuffer byteBuffer;
}
