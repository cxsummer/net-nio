package com.net.nio.service;

import com.net.nio.model.HttpResponseVO;

import java.util.LinkedHashMap;
import java.util.function.Consumer;

/**
 * @author caojiancheng
 * @date 2021/4/20
 * @description
 */
public interface HttpService {

    /**
     * http get请求
     *
     * @param uri
     * @param consumer
     * @param headers
     */
    void doGet(String uri, Consumer<HttpResponseVO> consumer, LinkedHashMap... headers);
}
