package com.net.nio.model;

import lombok.Data;

/**
 * @author caojiancheng
 * @date 2021/6/16
 * @description
 */
@Data
public class NioAddressVO {
    /**
     * 地址
     */
    private String host;
    /**
     * 端口
     */
    private int port;
    /**
     * 附加数据
     */
    private Object att;
}
