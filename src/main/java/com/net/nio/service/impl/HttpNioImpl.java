package com.net.nio.service.impl;

import com.net.nio.exception.CallBackException;
import com.net.nio.model.HttpRequestVO;
import com.net.nio.model.HttpResponseVO;
import com.net.nio.model.NioAddressVO;
import com.net.nio.service.NioAbstract;
import com.net.nio.service.SocketChannelPool;
import com.net.nio.service.SslService;
import com.net.nio.utils.Assert;
import com.net.nio.utils.GzipUtil;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static com.net.nio.utils.DataUtil.byteConcat;
import static com.net.nio.utils.DataUtil.byteExpansion;

/**
 * @author caojiancheng
 * @date 2021/5/11
 * @description
 */
public class HttpNioImpl extends NioAbstract {

    private SslService sslService;
    protected final String HTTP = "http";
    protected final String HTTPS = "https";
    protected SocketChannelPool<HttpRequestVO> socketChannelPool;

    public HttpNioImpl(SslService sslService, ExecutorService threadPool) {
        super(threadPool);
        this.sslService = sslService;
        this.socketChannelPool = new SocketChannelPoolImpl(1, selector);
    }

    @Override
    protected void connectHandler(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        if (socketChannel.finishConnect()) {
            selectionKey.interestOps(SelectionKey.OP_WRITE);
        } else {
            selectionKey.interestOps(SelectionKey.OP_CONNECT);
        }
    }

    @Override
    protected void writeHandler(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        HttpRequestVO httpRequestVO = (HttpRequestVO) selectionKey.attachment();
        ByteBuffer byteBuffer = requestByteBuffer(httpRequestVO, socketChannel);
        while (socketChannel.write(byteBuffer) > 0) {
            Thread.yield();
        }
        if (byteBuffer.hasRemaining()) {
            selectionKey.interestOps(SelectionKey.OP_WRITE);
        } else {
            HttpResponseVO httpResponseVO = new HttpResponseVO();
            httpResponseVO.setChunked("");
            httpResponseVO.setBodyIndex(0);
            httpResponseVO.setHeaderIndex(0);
            httpResponseVO.setOriginHeader(new byte[1024]);
            httpResponseVO.setHttpRequestVO(httpRequestVO);
            httpResponseVO.setCallBack(httpRequestVO.getCallBack());
            selectionKey.attach(httpResponseVO);
            selectionKey.interestOps(SelectionKey.OP_READ);
        }
    }

    /**
     * 读结束的标志：1.socketChannel.read返回为-1（发送方主动断开）。2.读取到足够的字节
     * 如果Content-Length存在，那么报文体的长度就是从head（第一个\r\n\r\n）后的字节数
     * 如果Transfer-Encoding等于chunked，那么报文体是分段返回的，从head（第一个\r\n\r\n）后开始，每一段的开始是  当前段长度（16进制）\r\n 结束是 \r\n 中间的字节就是段内容，其字节数等于当前段长度
     * 关闭socketChannel，当再次执行selector.select()时，会将此socketChannel从selector中移除，所以读取完毕后需要执行socketChannel.close()
     * 当调用SelectionKey的cancel()方法或关闭与SelectionKey关联的Channel或与SelectionKey关联的Selector被关闭。SelectionKey对象会失效，意味着Selector再也不会监控与它相关的事件
     * 但SelectionKey的cancel()并不会关闭连接，只是Selector再也不会监控与它相关的事件
     */
    @Override
    protected void readHandler(SelectionKey selectionKey) throws Exception {
        int num;
        ByteBuffer packetBuffer;
        ByteBuffer appBuffer = null;
        Integer contentLength = null;
        String transferEncoding = null;
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        HttpResponseVO httpResponseVO = (HttpResponseVO) selectionKey.attachment();
        SSLEngine sslEngine = httpResponseVO.getHttpRequestVO().getSslEngine();
        if (sslEngine == null) {
            packetBuffer = httpResponseVO.getPacketBuffer(10 * 1024);
        } else {
            appBuffer = httpResponseVO.getAppBuffer(sslEngine.getSession().getApplicationBufferSize());
            packetBuffer = httpResponseVO.getPacketBuffer(sslEngine.getSession().getPacketBufferSize());
        }
        Integer chunkedNum = Optional.of(httpResponseVO.getChunked()).filter(c -> c.contains("\r")).map(c -> Integer.parseInt(c.substring(0, c.indexOf("\r")), 16)).orElse(null);
        while ((num = socketChannel.read(packetBuffer)) != 0) {
            if (sslEngine == null) {
                appBuffer = packetBuffer;
            } else {
                SSLEngineResult res;
                packetBuffer.flip();
                do {
                    res = sslEngine.unwrap(packetBuffer, appBuffer);
                } while (res.getStatus() == SSLEngineResult.Status.OK);
                packetBuffer.compact();
            }
            for (int i = 0; i < appBuffer.position(); i++) {
                byte b = appBuffer.get(i);
                byte[] body = httpResponseVO.getBody();
                if (httpResponseVO.getHeaderIndex() > -1) {
                    headerHandler(b, httpResponseVO);
                } else {
                    LinkedHashMap<String, List<String>> headers = httpResponseVO.getHeaders();
                    transferEncoding = Optional.ofNullable(transferEncoding).orElseGet(() -> Optional.ofNullable(headers.get("Transfer-Encoding")).map(c -> c.get(0)).orElse(""));
                    contentLength = Optional.ofNullable(contentLength).orElseGet(() -> Optional.ofNullable(headers.get("Content-Length")).map(c -> Integer.parseInt(c.get(0))).orElse(-1));
                    if (contentLength != -1) {
                        body = body != null ? body : httpResponseVO.setGetBody(new byte[contentLength]);
                        body[httpResponseVO.getIncrementBodyIndex()] = b;
                        if (httpResponseVO.getBodyIndex().equals(contentLength)) {
                            num = -2;
                            break;
                        }
                    } else if ("chunked".equals(transferEncoding)) {
                        String chunked = httpResponseVO.getChunked();
                        if (chunked.endsWith("\r\n")) {
                            if (chunkedNum == 0) {
                                num = -2;
                                break;
                            }
                            body[httpResponseVO.getIncrementBodyIndex()] = b;
                            if (httpResponseVO.getBodyIndex() - httpResponseVO.getChunkedInitIndex() == chunkedNum) {
                                httpResponseVO.setChunked("");
                            }
                        } else if (!chunked.equals("") || (b != '\r' && b != '\n')) {
                            httpResponseVO.setChunked(chunked + new String(new byte[]{b}));
                            if (b == '\r') {
                                chunkedNum = Integer.parseInt(chunked, 16);
                                httpResponseVO.setBody(byteExpansion(body, chunkedNum));
                                httpResponseVO.setChunkedInitIndex(httpResponseVO.getBodyIndex());
                            }
                        }
                    }
                }
            }
            if (num < 0) {
                Assert.isIoTrue(httpResponseVO.getHeaders() != null, "服务器断开连接：" + num);
                socketChannelPool.close(socketChannel, c -> c.setSslEngine(sslEngine));
                contentDecode(httpResponseVO);
                doCallBack(httpResponseVO);
                return;
            }
            appBuffer.clear();
        }
        selectionKey.interestOps(SelectionKey.OP_READ);
    }

    @Override
    protected void exceptionHandler(SelectionKey selectionKey, Throwable e) {
        try {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            if (e instanceof IOException) {
                closeChannel(socketChannel);
            }
            int times = socketChannelPool.channelTimes(socketChannel);
            socketChannelPool.close(socketChannel, c -> c.setSslEngine(null));
            Assert.isTrue(times > 0, "服务器断开连接");
            HttpResponseVO httpResponseVO = (HttpResponseVO) selectionKey.attachment();
            HttpRequestVO httpRequestVO = httpResponseVO.getHttpRequestVO();
            httpRequestVO.setSslEngine(null);
            httpRequestVO.setByteBuffer(null);
            NioAddressVO address = new NioAddressVO();
            address.setAtt(httpRequestVO);
            address.setPort(httpRequestVO.getPort());
            address.setHost(httpRequestVO.getHost());
            socketChannelPool.submit(address);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private void doCallBack(HttpResponseVO httpResponseVO) {
        try {
            httpResponseVO.getCallBack().accept(httpResponseVO);
        } catch (Exception e) {
            throw new CallBackException(e);
        }
    }

    /**
     * 将请求转为http请求数组
     * todo headers里面的Transfer-Encoding为chunked时要按照格式分割报文体
     *
     * @param httpRequestVO
     * @return
     */
    private ByteBuffer requestByteBuffer(HttpRequestVO httpRequestVO, SocketChannel socketChannel) throws IOException {
        try {
            if (httpRequestVO.getByteBuffer() == null) {
                Map<String, Object> headers = Optional.ofNullable(httpRequestVO.getHeaders()).orElseGet(LinkedHashMap::new);
                headers.put("HOST", headers.getOrDefault("HOST", httpRequestVO.getHost()));
                headers.put("Content-Length", Optional.ofNullable(httpRequestVO.getBody()).map(b -> b.length).orElse(0));
                String requestLine = httpRequestVO.getMethod() + " " + httpRequestVO.getPath() + " HTTP/1.1 \r\n";
                byte[] request = headers.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining("\r\n", requestLine, "\r\n\r\n")).getBytes("UTF-8");
                ByteBuffer item = ByteBuffer.wrap(Optional.ofNullable(httpRequestVO.getBody()).map(b -> byteConcat(request, b)).orElse(request));
                if (HTTPS.equalsIgnoreCase(httpRequestVO.getProtocol())) {
                    SSLEngine sslEngine = httpRequestVO.getSslEngine();
                    if (sslEngine == null || sslEngine.isInboundDone() || sslEngine.isOutboundDone()) {
                        sslEngine = sslService.initSslEngine(httpRequestVO.getHost(), httpRequestVO.getPort());
                        sslService.sslHandshake(sslEngine, socketChannel);
                        httpRequestVO.setSslEngine(sslEngine);
                    }
                    ByteBuffer packetBuffer = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
                    SSLEngineResult res = sslEngine.wrap(item, packetBuffer);
                    Assert.isTrue(res.getStatus() == SSLEngineResult.Status.OK, "SSL握手失败");
                    packetBuffer.flip();
                    item = packetBuffer;
                }
                httpRequestVO.setByteBuffer(item);
            }
            return httpRequestVO.getByteBuffer();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * 解析出响应头
     *
     * @param b
     * @param httpResponseVO
     */
    private void headerHandler(byte b, HttpResponseVO httpResponseVO) {
        Integer headerIndex = httpResponseVO.getHeaderIndex();
        byte[] originHeader = httpResponseVO.getOriginHeader();
        if (originHeader.length == headerIndex) {
            originHeader = httpResponseVO.setGetOriginHeader(byteExpansion(originHeader, 1024));
        }
        originHeader[headerIndex++] = b;
        if (originHeader[headerIndex - 1] == '\n' && originHeader[headerIndex - 2] == '\r' && originHeader[headerIndex - 3] == '\n' && originHeader[headerIndex - 4] == '\r') {
            headerIndex = -1;
            String headerStr = new String(originHeader);
            String[] headerList = headerStr.split("\r\n");
            String[] responseLine = headerList[0].split(" ");
            httpResponseVO.setProtocol(responseLine[0]);
            httpResponseVO.setStatusMsg(responseLine[2]);
            httpResponseVO.setStatusCode(Integer.parseInt(responseLine[1]));
            httpResponseVO.setHeaders(Arrays.stream(headerList).skip(1).filter(h -> h.contains(":")).collect(Collectors.groupingBy(h -> h.split(":")[0].trim(), LinkedHashMap::new, Collectors.mapping(h -> h.split(":")[1].trim(), Collectors.toList()))));
        }
        httpResponseVO.setHeaderIndex(headerIndex);
    }

    /**
     * 解码报文体
     *
     * @param httpResponseVO
     */
    private void contentDecode(HttpResponseVO httpResponseVO) {
        String contentEncoding = Optional.ofNullable(httpResponseVO.getHeaders().get("Content-Encoding")).map(h -> h.get(0)).orElseGet(String::new);
        switch (contentEncoding) {
            case "gzip":
                httpResponseVO.setBody(GzipUtil.uncompress(httpResponseVO.getBody()));
                break;
        }
    }

}
