package com.net.nio.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author caojiancheng
 * @date 2021/4/26
 * @description
 */
@Data
public class HttpResponseVO {

    /**
     * 响应行
     */
    private String responseLine;

    /**
     * 状态码
     */
    private Integer statusCode;

    /**
     * 状态描述
     */
    private Integer statusMsg;

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
     * 原始响应头
     */
    private byte[] body;

    /**
     * 响应头
     */
    private LinkedHashMap headers;

}
