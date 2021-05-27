package com.net.nio;

import com.net.nio.service.HttpService;
import com.net.nio.service.NioAbstract;
import com.net.nio.service.SslService;
import com.net.nio.service.impl.HttpServiceImpl;
import com.net.nio.service.impl.SslServiceImpl;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLEngineResult;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
        AtomicInteger a=new AtomicInteger(0);
        LocalDateTime start = LocalDateTime.now();
        IntStream.range(0, 100).forEach(i -> {
                httpService.doGet("http://caibaojian.com/fedbook/tools/http.html", httpResponseVO -> {
                /*System.out.println(new String(httpResponseVO.getOriginHeader()));
                System.out.println(new String(httpResponseVO.getBody()));*/
                    System.out.println(a.incrementAndGet()+"用时：" + Duration.between(start, LocalDateTime.now()).toMillis());
                }, new LinkedHashMap() {{
                    /*put("Accept-Encoding", "gzip, deflate");*/
                    put("cookie", "_uuid=ED61076A-4384-3871-77CF-1D5DAC79E25F20193infoc; buvid3=A1F3EA2A-EEF0-4B9B-AD9C-5B92AEEAC7BE18553infoc; CURRENT_FNVAL=80; blackside_state=1; rpdid=|(u~|~~km~)R0J'uYuR~RlJll; buvid_fp=A1F3EA2A-EEF0-4B9B-AD9C-5B92AEEAC7BE18553infoc; buvid_fp_plain=328CBAF9-0B0F-4ED8-8F1B-AE9C1CA827F718564infoc; fingerprint=7a8d81b2349aee547658234afb8661fa; PVID=1; bsource=search_baidu; sid=b58ekf8m; CURRENT_QUALITY=0");
                }});
        });
        /*Thread.sleep(2000);
        ((NioAbstract) httpService).stopNioMonitor();
        threadPool.shutdownNow();*/

    }


}
