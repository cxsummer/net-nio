package com.net.nio.service.impl;

import com.net.nio.model.HttpRequestVO;
import com.net.nio.service.HttpService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author caojiancheng
 * @date 2021/4/20
 * @description
 */
public class HttpServiceImpl implements HttpService {

    private Selector selector;

    public HttpServiceImpl() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void nioMonitor() throws IOException {
        while (selector.select() > 0) {
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();
                if (selectionKey.isWritable()) {
                    ByteBuffer byteBuffer;
                    selectionKey.interestOps(0);
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    if (selectionKey.attachment() instanceof ByteBuffer) {
                        byteBuffer = (ByteBuffer) selectionKey.attachment();
                    } else {
                        HttpRequestVO httpRequestVO = (HttpRequestVO) selectionKey.attachment();
                        Map<String, Object> headers = Optional.ofNullable(httpRequestVO.getHeaders()).orElseGet(LinkedHashMap::new);
                        headers.put("HOST", headers.getOrDefault("HOST", httpRequestVO.getAddress()));

                        String requestLine = httpRequestVO.getMethod() + " " + httpRequestVO.getQueryString() + " HTTP/1.1 \r\n";
                        String requestStr = headers.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining("\r\n", requestLine, "\r\n\r\n"));
                        byteBuffer = ByteBuffer.wrap(requestStr.getBytes("UTF-8"));
                    }
                    socketChannel.write(byteBuffer);
                    if (!byteBuffer.hasRemaining()) {
                        selectionKey.interestOps(SelectionKey.OP_READ);
                    } else {
                        selectionKey.attach(byteBuffer);
                        selectionKey.interestOps(SelectionKey.OP_WRITE);
                    }
                } else if (selectionKey.isReadable()) {
                    selectionKey.interestOps(0);
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024);
                    int successBytes = socketChannel.read(byteBuffer);
                    byteBuffer.flip();
                    byte[] b = new byte[byteBuffer.limit()];
                    byteBuffer.get(b);
                    System.out.print(new String(b, "utf-8"));
                }
            }
        }
    }

    public void doGet(String uri, LinkedHashMap... headers) {
        try {
            int port = 80;
            int pathIndex = uri.indexOf("/");
            String address = uri.substring(0, pathIndex);
            int portIndex = address.indexOf(":");
            if (portIndex > -1) {
                port = Integer.parseInt(address.substring(portIndex + 1));
                address = address.substring(0, portIndex);
            }
            HttpRequestVO httpRequestVO = new HttpRequestVO();
            httpRequestVO.setPort(port);
            httpRequestVO.setMethod("GET");
            httpRequestVO.setAddress(address);
            httpRequestVO.setQueryString(uri.substring(pathIndex));
            Optional.ofNullable(headers).filter(Objects::nonNull).filter(h -> h.length > 0).map(h -> h[0]).ifPresent(httpRequestVO::setHeaders);

            SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(address, port));
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_WRITE, httpRequestVO);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        HttpServiceImpl httpService = new HttpServiceImpl();
        httpService.doGet("www.tietuku.com/album/1735537");
        httpService.nioMonitor();
    }

}
