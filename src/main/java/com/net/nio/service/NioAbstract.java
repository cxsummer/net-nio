package com.net.nio.service;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author caojiancheng
 * @date 2021/5/2
 * @description
 */
public abstract class NioAbstract {

    protected Selector selector;
    protected ExecutorService threadLocal = new ThreadPoolExecutor(50,
            50,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());

    public NioAbstract() {
        try {
            selector = Selector.open();
            threadLocal.submit(this::startNioMonitor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 因为select方法执行的是lockAndDoSelect方法，里面是用的synchronized锁住的SelectorImpl类的publicKeys变量
     * 那么selector.select就不能阻塞，因为一旦selector阻塞，而根据锁的可重入性，当前线程可以直接执行但是别的线程需要等待锁的释放
     * select先执行，如果selector.select查询不到可用的连接，那么就不会释放锁，而只有向selector注册连接才会有可用连接
     * 即socketChannel.register(selector)，这个方法也需要获取publicKeys对象锁，又因为是其他线程，这样就会造成死锁
     * 所以要用selector.selectNow方法，查询到或者查不到都会释放锁，不会在获取到锁后阻塞
     */
    private void startNioMonitor() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (selector.selectNow() <= 0) {
                    continue;
                }
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();
                    if (selectionKey.isWritable()) {
                        selectionKey.interestOps(0);
                        writeHandler(selectionKey);
                    } else if (selectionKey.isReadable()) {
                        selectionKey.interestOps(0);
                        readHandler(selectionKey);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected void stopNioMonitor() {

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
