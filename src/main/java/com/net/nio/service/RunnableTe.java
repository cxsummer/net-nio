package com.net.nio.service;

import com.net.nio.utils.Assert;

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
     * @param consumers  如果有多个异常处理，自行执行consumer的andThen方法
     * @return
     */
    static Runnable exchangeRunnable(RunnableTe runnableTe, Consumer<Exception>... consumers) {
        Assert.isTrue(consumers.length < 2, "consumers最多只能有1个");
        return () -> {
            try {
                runnableTe.run();
            } catch (Exception e) {
                if (consumers.length > 0) {
                    consumers[0].accept(e);
                }
            }
        };
    }
}
