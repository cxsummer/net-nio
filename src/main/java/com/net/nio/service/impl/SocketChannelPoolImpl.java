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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author caojiancheng
 * @date 2021/6/9
 * @description
 */
public class SocketChannelPoolImpl implements SocketChannelPool {
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
        int port = address.getPort();
        Object att = address.getAtt();
        String host = address.getHost();
        String key = host + ":" + port;
        AtomicReference<Channel> minChannelReference = new AtomicReference<>();
        boolean findSameChanel = pool.entrySet().stream().filter(e -> e.getValue() != null).anyMatch(e -> {
            Iterator<Channel> iterator = e.getValue().iterator();
            while (iterator.hasNext()) {
                Channel channel = iterator.next();
                if (channel.getFree() == 1) {
                    if (channel.isValid() && key.equals(e.getKey())) {
                        try {
                            channel.setFree(0);
                            channel.getSocketChannel().register(selector, SelectionKey.OP_WRITE, att);
                            return true;
                        } catch (ClosedChannelException closedChannelException) {
                            closedChannelException.printStackTrace();
                        }
                    } else {
                        Channel minChannel = minChannelReference.get();
                        minChannelReference.set(minChannel == null ? channel : Stream.of(minChannel, channel).min(Comparator.comparingLong(Channel::getAddTime)).get());
                    }
                }
            }
            return false;
        });
        if (findSameChanel) {
            return;
        }
        List<Channel> channelList = pool.get(key);
        Channel minChannel = minChannelReference.get();
        int activeSize = pool.values().stream().filter(Objects::nonNull).mapToInt(List::size).sum();
        if (activeSize < poolSize || minChannel != null) {
            if (channelList == null) {
                channelList = new ArrayList();
                pool.put(key, channelList);
            }
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(host, port));
            Channel channelPoolItem = Optional.ofNullable(minChannel).filter(a -> activeSize == poolSize).orElseGet(Channel::new);
            channelPoolItem.setFree(0);
            channelPoolItem.setAddTime(System.currentTimeMillis());
            channelPoolItem.setSocketChannel(socketChannel);
            channelList.add(channelPoolItem);
            socketChannel.register(selector, SelectionKey.OP_CONNECT, att);
        } else {
            addressList.add(address);
        }
    }

    @Override
    public synchronized void close(SocketChannel socketChannel, Consumer attConsumer) throws IOException {
        pool.values().stream().filter(Objects::nonNull).flatMap(Collection::stream).filter(c -> c.getSocketChannel() == socketChannel).findFirst().get().setFree(1);
        if (addressList.size() > 0) {
            NioAddressVO address = addressList.get(0);
            attConsumer.accept(address.getAtt());
            submit(address);
            addressList.remove(0);
        }
    }

    @Data
    private class Channel {
        /**
         * 是否空闲0：否，1是
         */
        private int free;

        /**
         * 添加时间
         */
        private long addTime;

        /**
         * 连接通道
         */
        private SocketChannel socketChannel;

        public boolean isValid() {
            try {
                ByteBuffer b = ByteBuffer.allocate(1);
                return socketChannel.read(b) == -1 ? false : true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}
