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
            threadLocal.submit(this::nioMonitor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 如果执行selector.select的方法的线程和向selector注册socketChannel的线程不是同一个线程的话
     * 那么selector.select就不能阻塞，因为一旦selector阻塞，而根据对象的可重入性，那么别的线程需要等待锁的释放
     * 但是如果selector.select查询不到可用的连接，那么就不会释放锁，而只有向selector注册连接才会有可用连接，这样就会造成死锁
     * 所以要用selector.selectNow方法，不阻塞
     */
    private void nioMonitor() {
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
