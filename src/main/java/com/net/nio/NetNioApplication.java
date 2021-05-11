package com.net.nio;

import com.net.nio.service.HttpService;
import com.net.nio.service.NioAbstract;
import com.net.nio.service.SslService;
import com.net.nio.service.impl.HttpServiceImpl;
import com.net.nio.service.impl.SslServiceImpl;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * @author caojiancheng
 * @date 2021/4/20
 * @description
 */
@Slf4j
public class NetNioApplication {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService threadPool = new ThreadPoolExecutor(50,
                50,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());

        SslService sslService = new SslServiceImpl();
        HttpService httpService = new HttpServiceImpl(sslService, threadPool);
        IntStream.range(0, 1).forEach(i -> {
            LocalDateTime start = LocalDateTime.now();
            httpService.doGet("https://www.ximalaya.com/youshengshu/41594201/340652654", httpResponseVO -> {
                System.out.println(new String(httpResponseVO.getOriginHeader()));
                String res = new String(httpResponseVO.getBody());
                System.out.println(res);
                System.out.println(Duration.between(start, LocalDateTime.now()).toMillis());
            }, new LinkedHashMap() {{
                put("Accept-Encoding", "gzip, deflate");
            }});
        });
        Thread.sleep(2000);
        ((NioAbstract) httpService).stopNioMonitor();
        threadPool.shutdownNow();
    }


}
