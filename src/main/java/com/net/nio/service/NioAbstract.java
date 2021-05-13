package com.net.nio.service;

import com.net.nio.NetNioApplication;
import com.net.nio.model.HttpRequestVO;
import com.net.nio.model.HttpResponseVO;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import static com.net.nio.service.RunnableTe.exchangeRunnable;

/**
 * @author caojiancheng
 * @date 2021/5/2
 * @description
 */
@Slf4j
public abstract class NioAbstract {

    protected Selector selector;
    private Thread nioMonitorThread;
    protected ExecutorService threadPool;

    public NioAbstract(ExecutorService threadPool) {
        try {
            this.threadPool = threadPool;
            this.selector = Selector.open();
            threadPool.submit(this::startNioMonitor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 开启nio监听
     * 因为select方法执行的是lockAndDoSelect方法，里面是用的synchronized锁住的SelectorImpl类的publicKeys变量
     * 那么selector.select就不能阻塞，因为一旦selector阻塞，而根据锁的可重入性，当前线程可以直接执行但是别的线程需要等待锁的释放
     * select先执行，如果selector.select查询不到可用的连接，那么就不会释放锁，而只有向selector注册连接才会有可用连接
     * 即socketChannel.register(selector)，这个方法也需要获取publicKeys对象锁，又因为是其他线程，这样就会造成死锁
     * 所以要用selector.selectNow方法，查询到或者查不到都会释放锁，不会在获取到锁后阻塞
     */
    private void startNioMonitor() {
        nioMonitorThread = Thread.currentThread();
        while (!nioMonitorThread.isInterrupted()) {
            try {
                if (selector.selectNow() <= 0) {
                    Thread.yield();
                    continue;
                }
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();
                    if (selectionKey.isWritable()) {
                        selectionKey.interestOps(0);
                        HttpRequestVO httpRequestVO = (HttpRequestVO) selectionKey.attachment();
                        threadPool.submit(exchangeRunnable(() -> writeHandler(selectionKey), httpRequestVO.getExceptionHandler(), selectionKey::cancel));
                    } else if (selectionKey.isReadable()) {
                        selectionKey.interestOps(0);
                        HttpResponseVO httpResponseVO = (HttpResponseVO) selectionKey.attachment();
                        threadPool.submit(exchangeRunnable(() -> readHandler(selectionKey), httpResponseVO.getExceptionHandler(), selectionKey::cancel));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        log.info("停止nio监听");
    }


    /**
     * 停止nio监听
     */
    public void stopNioMonitor() {
        nioMonitorThread.interrupt();
    }

    /**
     * 写操作
     *
     * @param selectionKey
     * @throws IOException
     */
    protected abstract void writeHandler(SelectionKey selectionKey) throws IOException;

    /**
     * 读操作
     *
     * @param selectionKey
     * @throws IOException
     */
    protected abstract void readHandler(SelectionKey selectionKey) throws IOException;
}
