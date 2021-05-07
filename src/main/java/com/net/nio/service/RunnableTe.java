package com.net.nio.service;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author caojiancheng
 * @date 2021/5/3
 * @description
 */
@FunctionalInterface
public interface RunnableTe {
    /**
     * 向上抛异常的Runnable
     *
     * @throws Exception
     */
    void run() throws Exception;

    /**
     * RunnableTe转Runnable
     *
     * @param runnableTe
     * @param function
     * @return
     */
    static Runnable exchangeRunnable(RunnableTe runnableTe, Function<Exception, ?>... function) {
        return () -> {
            try {
                runnableTe.run();
            } catch (Exception e) {
                if (function.length > 0) {
                    function[0].apply(e);
                }
            }
        };
    }

    /**
     * RunnableTe转Runnable
     *
     * @param runnableTe
     * @param consumers
     * @return
     */
    static Runnable exchangeRunnable(RunnableTe runnableTe, Consumer<Exception> consumers) {
        return () -> {
            try {
                runnableTe.run();
            } catch (Exception e) {
                consumers.accept(e);
            }
        };
    }
}
