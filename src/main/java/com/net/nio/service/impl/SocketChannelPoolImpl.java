package com.net.nio.service.impl;

import com.net.nio.model.NioAddressVO;
import com.net.nio.service.SocketChannelPool;
import lombok.Data;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author caojiancheng
 * @date 2021/6/9
 * @description
 */
public class SocketChannelPoolImpl<T> implements SocketChannelPool<T> {
    private int poolSize;
    private Selector selector;
    private List<NioAddressVO> addressList;
    private Map<String, List<Channel>> pool;

    public SocketChannelPoolImpl(int poolSize, Selector selector) {
        this.poolSize = poolSize;
        this.selector = selector;
        this.pool = new HashMap<>();
        this.addressList = new ArrayList<>();
    }

    @Override
    public synchronized void submit(NioAddressVO address) throws IOException {
        Channel minChannel = null;
        int port = address.getPort();
        Object att = address.getAtt();
        String host = address.getHost();
        String key = host + ":" + port;
        for (Map.Entry<String, List<Channel>> entry : pool.entrySet()) {
            for (Channel channel : entry.getValue()) {
                if (channel.getFree() == 1) {
                    if (channel.isValid() && key.equals(entry.getKey())) {
                        try {
                            channel.setFree(0);
                            channel.getAttConsumer().accept(att);
                            channel.setTimes(channel.getTimes() + 1);
                            channel.getSocketChannel().register(selector, SelectionKey.OP_WRITE, att);
                            return;
                        } catch (ClosedChannelException closedChannelException) {
                            closedChannelException.printStackTrace();
                        }
                    } else {
                        minChannel = minChannel == null ? channel : Stream.of(minChannel, channel).min(Comparator.comparingLong(Channel::getAddTime)).get();
                    }
                }
            }
        }
        List<Channel> channelList = pool.get(key);
        int activeSize = pool.values().stream().filter(Objects::nonNull).mapToInt(List::size).sum();
        System.out.println("活跃的数量" + activeSize);
        if (activeSize < poolSize || minChannel != null) {
            if (channelList == null) {
                channelList = new ArrayList();
                pool.put(key, channelList);
            }
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(host, port));
            if (activeSize < poolSize) {
                minChannel = new Channel();
                channelList.add(minChannel);
            }
            minChannel.setFree(0);
            minChannel.setTimes(0);
            minChannel.setAttConsumer(null);
            minChannel.setSocketChannel(socketChannel);
            minChannel.setAddTime(System.currentTimeMillis());
            socketChannel.register(selector, SelectionKey.OP_CONNECT, att);
            System.out.println("创建连接");
        } else {
            addressList.add(address);
        }
    }

    @Override
    public synchronized void close(SocketChannel socketChannel, Consumer<T> attConsumer) throws IOException {
        Channel channel = getChannelFromSocket(socketChannel);
        channel.setFree(1);
        channel.setAttConsumer(attConsumer);
        if (addressList.size() > 0) {
            NioAddressVO address = addressList.get(0);
            submit(address);
            addressList.remove(0);
        }
    }


    @Override
    public int channelTimes(SocketChannel socketChannel) {
        return getChannelFromSocket(socketChannel).getTimes();
    }

    private Channel getChannelFromSocket(SocketChannel socketChannel) {
        return pool.values().stream().filter(Objects::nonNull).flatMap(Collection::stream).filter(c -> c.getSocketChannel() == socketChannel).findFirst().get();
    }

    @Data
    private class Channel {
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
        Consumer attConsumer;

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
