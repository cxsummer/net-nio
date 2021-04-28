package com.net.nio.service.impl;

import com.net.nio.model.HttpRequestVO;
import com.net.nio.model.HttpResponseVO;
import com.net.nio.service.HttpService;

import java.io.FileOutputStream;
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
                    while (socketChannel.write(byteBuffer) > 0) {
                    }
                    if (!byteBuffer.hasRemaining()) {
                        HttpResponseVO httpResponseVO = new HttpResponseVO();
                        httpResponseVO.setHeaderIndex(0);
                        httpResponseVO.setOriginHeader(new byte[1024]);
                        httpResponseVO.setBodyIndex(0);
                        httpResponseVO.setBody(new byte[1024]);
                        httpResponseVO.setChunked("");
                        selectionKey.attach(httpResponseVO);
                        selectionKey.interestOps(SelectionKey.OP_READ);
                    } else {
                        selectionKey.attach(byteBuffer);
                        selectionKey.interestOps(SelectionKey.OP_WRITE);
                    }
                } else if (selectionKey.isReadable()) {
                    selectionKey.interestOps(0);
                    Integer contentLength = null;
                    String transferEncoding = null;
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024);
                    HttpResponseVO httpResponseVO = (HttpResponseVO) selectionKey.attachment();
                    byte[] body = httpResponseVO.getBody();
                    Integer bodyIndex = httpResponseVO.getBodyIndex();
                    byte[] originHeader = httpResponseVO.getOriginHeader();
                    Integer headerIndex = httpResponseVO.getHeaderIndex();
                    while (socketChannel.read(byteBuffer) > 0) {
                        byteBuffer.flip();
                        for (int i = 0; i < byteBuffer.limit(); i++) {
                            if (headerIndex > -1) {
                                if (originHeader.length == headerIndex) {
                                    byte[] temp = new byte[originHeader.length + 1024];
                                    for (int j = 0; j < originHeader.length; j++) {
                                        temp[j] = originHeader[j];
                                    }
                                    originHeader = temp;
                                    httpResponseVO.setOriginHeader(originHeader);
                                }
                                originHeader[headerIndex++] = byteBuffer.get(i);
                                if (originHeader[headerIndex - 1] == '\n' && originHeader[headerIndex - 2] == '\r' && originHeader[headerIndex - 3] == '\n' && originHeader[headerIndex - 4] == '\r') {
                                    headerIndex = -1;
                                    String headerStr = new String(originHeader);
                                    String[] headerList = headerStr.split("\r\n");
                                    String[] responseLine = headerList[0].split(" ");
                                    httpResponseVO.setProtocol(responseLine[0]);
                                    httpResponseVO.setStatusMsg(responseLine[2]);
                                    httpResponseVO.setStatusCode(Integer.parseInt(responseLine[1]));
                                    httpResponseVO.setHeaders(Arrays.stream(headerList).skip(1).filter(h -> h.contains(":")).collect(Collectors.groupingBy(h -> h.split(":")[0], LinkedHashMap::new, Collectors.mapping(h -> h.split(":")[1].trim(), Collectors.toList()))));
                                }
                                httpResponseVO.setHeaderIndex(headerIndex);
                            } else {
                                LinkedHashMap<String, List<String>> headers = httpResponseVO.getHeaders();
                                transferEncoding = Optional.ofNullable(transferEncoding).orElseGet(() -> Optional.ofNullable(headers.get("Transfer-Encoding")).filter(Objects::nonNull).map(c -> c.get(0)).orElse(null));
                                contentLength = Optional.ofNullable(contentLength).orElseGet(() -> Optional.ofNullable(headers.get("Content-Length")).filter(Objects::nonNull).map(c -> Integer.parseInt(c.get(0))).orElse(null));

                                if (body.length == bodyIndex) {
                                    byte[] temp = new byte[body.length + 1024];
                                    for (int j = 0; j < body.length; j++) {
                                        temp[j] = body[j];
                                    }
                                    body = temp;
                                    httpResponseVO.setBody(body);
                                }

                                byte b = byteBuffer.get(i);
                                if (contentLength != null) {
                                    body[bodyIndex++] = b;
                                    httpResponseVO.setBodyIndex(bodyIndex);
                                    if (bodyIndex.equals(contentLength)) {
                                        //关闭socketChannel，当再次执行selector.select()时，会将此socketChannel从selector中移除
                                        socketChannel.close();
                                        System.out.println(new String(body));
                                        return;
                                    }
                                } else if ("chunked".equals(transferEncoding)) {
                                    String chunked = httpResponseVO.getChunked();
                                    if (chunked.endsWith("\r\n")) {
                                        if (chunked.equals("\r\n")) {
                                            socketChannel.close();
                                            System.out.println(new String(body));
                                            return;
                                        }
                                        Integer chunkedNum = Integer.parseInt(chunked.replace("\r\n", ""), 16);
                                        body[bodyIndex++] = b;
                                        if (bodyIndex - httpResponseVO.getChunkedInitIndex() == chunkedNum) {
                                            httpResponseVO.setChunked("");
                                        }
                                    } else {
                                        httpResponseVO.setChunkedInitIndex(bodyIndex);
                                        httpResponseVO.setChunked(chunked + new String(new byte[]{b}));
                                    }
                                }
                            }
                        }
                        byteBuffer.clear();
                    }
                    selectionKey.interestOps(SelectionKey.OP_READ);
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
        httpService.doGet("www.tietuku.com/album/1735537-2");
        httpService.nioMonitor();
    }

}
