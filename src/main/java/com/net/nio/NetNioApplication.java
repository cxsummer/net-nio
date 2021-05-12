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
            httpService.doGet("https://www.bilibili.com/video/BV1w64y11787?from=search&seid=5135759427111210711&rt=V%2FymTlOu4ow%2Fy4xxNWPUZ7%2BHnbxzryomN1iwzhhMkuE%3D", httpResponseVO -> {
                System.out.println(new String(httpResponseVO.getOriginHeader()));
                String res = new String(httpResponseVO.getBody());
                System.out.println(res);
                System.out.println(Duration.between(start, LocalDateTime.now()).toMillis());
            }, new LinkedHashMap() {{
                put("Accept-Encoding", "gzip, deflate");
                put("cookie","_uuid=ED61076A-4384-3871-77CF-1D5DAC79E25F20193infoc; buvid3=A1F3EA2A-EEF0-4B9B-AD9C-5B92AEEAC7BE18553infoc; CURRENT_FNVAL=80; blackside_state=1; rpdid=|(u~|~~km~)R0J'uYuR~RlJll; buvid_fp=A1F3EA2A-EEF0-4B9B-AD9C-5B92AEEAC7BE18553infoc; buvid_fp_plain=328CBAF9-0B0F-4ED8-8F1B-AE9C1CA827F718564infoc; fingerprint=7a8d81b2349aee547658234afb8661fa; PVID=1; bsource=search_baidu; sid=b58ekf8m; CURRENT_QUALITY=0");
            }});
        });
        /*Thread.sleep(2000);
        ((NioAbstract) httpService).stopNioMonitor();
        threadPool.shutdownNow();*/
    }


}
