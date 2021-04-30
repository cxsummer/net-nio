package com.net.nio.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author caojiancheng
 * @date 2021/4/26
 * @description
 */
@Data
public class HttpResponseVO {

    /**
     * 协议
     */
    private String protocol;

    /**
     * 状态码
     */
    private Integer statusCode;

    /**
     * 状态描述
     */
    private String statusMsg;

    /**
     * 原始响应头下标
     */
    private Integer headerIndex;

    /**
     * 原始响应头
     */
    private byte[] originHeader;

    /**
     * 响应体下标
     */
    private Integer bodyIndex;

    /**
     * 响应头
     */
    private LinkedHashMap<String, List<String>> headers;

    /**
     * 响应体
     */
    private byte[] body;

    /**
     * 分段长度
     */
    private String chunked;


    /**
     * 分段在body的原始位置
     */
    private Integer chunkedInitIndex;

    /**
     * 回调方法
     */
    Consumer<HttpResponseVO> consumer;

    public byte[] setGetBody(byte[] body) {
        this.body = body;
        return body;
    }

    public byte[] setGetOriginHeader(byte[] originHeader) {
        this.originHeader = originHeader;
        return originHeader;
    }

    public Integer getIncrementBodyIndex() {
        this.bodyIndex++;
        return bodyIndex - 1;
    }
}
