package com.net.nio;

import com.net.nio.service.HttpService;
import com.net.nio.service.NioAbstract;
import com.net.nio.service.impl.HttpServiceImpl;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
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
        IntStream.range(0, 2).forEach(i -> {
            httpService.doGet("www.tietuku.com/album/1735537-2", httpResponseVO -> {
                System.out.println(new String(httpResponseVO.getBody()));
            });
        });
        Thread.sleep(2000);
        ((NioAbstract) httpService).stopNioMonitor();
        threadPool.shutdownNow();
    }


}
