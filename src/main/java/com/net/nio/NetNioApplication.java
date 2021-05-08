package com.net.nio;

import com.net.nio.service.HttpService;
import com.net.nio.service.NioAbstract;
import com.net.nio.service.impl.HttpServiceImpl;
import lombok.extern.slf4j.Slf4j;

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

        HttpService httpService = new HttpServiceImpl(threadPool);
        IntStream.range(0, 1).forEach(i -> {
            httpService.doGet("https://www.cnblogs.com/hanfanfan/p/9565089.html", httpResponseVO -> {
                System.out.println(new String(httpResponseVO.getOriginHeader()));
                String res= new String(httpResponseVO.getBody());
                System.out.println(res);
            },new LinkedHashMap(){{
                put("Accept-Encoding","gzip, deflate");
            }});
        });
        Thread.sleep(5000);
        ((NioAbstract) httpService).stopNioMonitor();
        threadPool.shutdownNow();
    }


}
