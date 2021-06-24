package com.net.nio.model;

import lombok.Data;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author caojiancheng
 * @date 2021/5/27
 * @description
 */
@Data
public class BaseNetVO {

    /**
     * 回调方法
     */
    private BiConsumer<HttpResponseVO, Exception> callBack;
}
