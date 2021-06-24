package com.net.nio.service;

import com.net.nio.model.NioAddressVO;
import com.net.nio.service.impl.SocketChannelPoolImpl;
import lombok.Data;

import java.io.IOException;
import java.nio.ByteBuffer;
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
     * @param socketChannel 连接
     * @return
     * @throws IOException
     */
    void close(SocketChannel socketChannel) throws IOException;

    /**
     * 获取通道
     *
     * @param socketChannel
     * @return
     */
    Channel getChannelFromSocket(SocketChannel socketChannel);


    @Data
    class Channel<T> {
        /**
         * 是否空闲0：否，1是
         */
        private int free;

        /**
         * 使用次数
         */
        private int times;

        /**
         * 添加时间
         */
        private long addTime;

        /**
         * 处理附加数据
         */
        Consumer<T> attConsumer;

        /**
         * 连接通道
         */
        private SocketChannel socketChannel;

        /**
         * 双方都没有关闭代表可用
         */
        public boolean isValid() {
            try {
                ByteBuffer b = ByteBuffer.allocate(1);
                return socketChannel.isOpen() && socketChannel.read(b) > -1 ? true : false;
            } catch (IOException e) {
                return false;
            }
        }
    }
}
