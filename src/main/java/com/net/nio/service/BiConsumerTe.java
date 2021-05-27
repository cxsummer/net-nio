package com.net.nio.service;

/**
 * @author caojiancheng
 * @date 2021/5/3
 * @description
 */
@FunctionalInterface
public interface BiConsumerTe<T, U> {
    /**
     * 向上抛异常的BiConsumer
     *
     * @param t
     * @param u
     * @throws Exception
     */
    void accept(T t, U u) throws Exception;
}
