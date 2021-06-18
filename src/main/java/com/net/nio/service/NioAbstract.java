package com.net.nio.service;

import com.net.nio.NetNioApplication;
import com.net.nio.model.BaseNetVO;
import com.net.nio.model.HttpRequestVO;
import com.net.nio.model.HttpResponseVO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.net.nio.service.SelectionHandler.forEachHandler;

/**
 * @author caojiancheng
 * @date 2021/5/2
 * @description
 */
@Slf4j
public abstract class NioAbstract {

    protected Selector selector;
    private Thread nioMonitorThread;
    private Method getExceptionHandler;
    protected ExecutorService threadPool;

    public NioAbstract(ExecutorService threadPool) {
        try {
            this.threadPool = threadPool;
            this.selector = Selector.open();
            threadPool.submit(this::startNioMonitor);
            getExceptionHandler = BaseNetVO.class.getMethod("getExceptionHandler");
        } catch (IOException | NoSuchMethodException e) {
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
                    forEachHandler(this, selectionKey, (Consumer<Exception>) getExceptionHandler.invoke(selectionKey.attachment()), threadPool);
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
     * 连接操作
     *
     * @param selectionKey
     * @throws IOException
     */
    protected abstract void connectHandler(SelectionKey selectionKey) throws IOException;

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

/**
 * 为防止java.nio.channels.CancelledKeyException错误
 * 要按照SelectionKey的isConnectable，isWritable，isReadable的顺序执行
 */
@Getter
@AllArgsConstructor
enum SelectionHandler {
    connectable(SelectionKey::isConnectable, NioAbstract::connectHandler),
    writable(SelectionKey::isWritable, NioAbstract::writeHandler),
    readable(SelectionKey::isReadable, NioAbstract::readHandler);

    private Predicate<SelectionKey> predicate;
    private BiConsumerTe<NioAbstract, SelectionKey> biConsumerTe;

    public static void forEachHandler(NioAbstract nioAbstract, SelectionKey selectionKey, Consumer<Exception> exceptionHandler, ExecutorService threadPool) {
        Arrays.stream(SelectionHandler.values()).filter(s -> s.getPredicate().test(selectionKey)).peek(s -> selectionKey.interestOps(0)).forEach(s -> threadPool.submit(() -> {
            try {
                s.getBiConsumerTe().accept(nioAbstract, selectionKey);
            } catch (Exception e) {
                exceptionHandler.accept(e);
            }
        }));
    }
}
