package com.net.nio.service;

import com.net.nio.model.NioAddressVO;
import com.net.nio.service.impl.SocketChannelPoolImpl;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author caojiancheng
 * @date 2021/6/9
 * @description
 */
public interface SocketChannelPool {
    /**
     * 从连接池中拿连接
     *
     * @param address 地址信息
     * @return
     * @throws IOException
     */
    void submit(NioAddressVO address) throws IOException;

    /**
     * 返还给连接池
     *
     * @param selectionKey 连接
     * @param attConsumer
     * @return
     * @throws IOException
     */
    void close(SelectionKey selectionKey, Consumer attConsumer) throws IOException;
}
