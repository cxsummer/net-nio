package com.net.nio.service.impl;

import com.net.nio.model.HttpRequestVO;
import com.net.nio.model.HttpResponseVO;
import com.net.nio.service.HttpService;
import com.net.nio.service.NioAbstract;
import com.net.nio.utils.GzipUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.net.nio.utils.DataUtil.byteConcat;
import static com.net.nio.utils.DataUtil.byteExpansion;

/**
 * @author caojiancheng
 * @date 2021/4/20
 * @description
 */
public class HttpServiceImpl extends NioAbstract implements HttpService {

    public HttpServiceImpl(ExecutorService threadPool) {
        super(threadPool);
    }

    @Override
    protected void writeHandler(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        HttpRequestVO httpRequestVO = (HttpRequestVO) selectionKey.attachment();
        ByteBuffer byteBuffer = requestByteBuffer(httpRequestVO);
        while (socketChannel.write(byteBuffer) > 0) {
        }
        if (!byteBuffer.hasRemaining()) {
            HttpResponseVO httpResponseVO = new HttpResponseVO();
            httpResponseVO.setChunked("");
            httpResponseVO.setBodyIndex(0);
            httpResponseVO.setHeaderIndex(0);
            httpResponseVO.setBody(new byte[1024]);
            httpResponseVO.setOriginHeader(new byte[1024]);
            httpResponseVO.setCallBack(httpRequestVO.getCallBack());
            httpResponseVO.setExceptionHandler(httpRequestVO.getExceptionHandler());
            selectionKey.attach(httpResponseVO);
            selectionKey.interestOps(SelectionKey.OP_READ);
        } else {
            selectionKey.interestOps(SelectionKey.OP_WRITE);
        }
    }

    /**
     * 如果Content-Length存在，那么报文体的长度就是从head（第一个\r\n\r\n）后的字节数
     * 如果Transfer-Encoding等于chunked，那么报文体是分段返回的，从head（第一个\r\n\r\n）后开始，每一段的开始是  当前段长度（16进制）\r\n 结束是 \r\n 中间的字节就是段内容，其字节数等于当前段长度
     * 关闭socketChannel，当再次执行selector.select()时，会将此socketChannel从selector中移除，所以读取完毕后需要执行socketChannel.close()
     */
    @Override
    protected void readHandler(SelectionKey selectionKey) throws IOException {
        boolean isEnd = false;
        Integer chunkedNum = null;
        Integer contentLength = null;
        String transferEncoding = null;
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024);
        HttpResponseVO httpResponseVO = (HttpResponseVO) selectionKey.attachment();
        byte[] body = httpResponseVO.getBody();
        while (socketChannel.read(byteBuffer) > 0) {
            byteBuffer.flip();
            for (int i = 0; i < byteBuffer.limit(); i++) {
                byte b = byteBuffer.get(i);
                if (httpResponseVO.getHeaderIndex() > -1) {
                    headerHandler(b, httpResponseVO);
                } else {
                    LinkedHashMap<String, List<String>> headers = httpResponseVO.getHeaders();
                    transferEncoding = Optional.ofNullable(transferEncoding).orElseGet(() -> Optional.ofNullable(headers.get("Transfer-Encoding")).map(c -> c.get(0)).orElse(""));
                    contentLength = Optional.ofNullable(contentLength).orElseGet(() -> Optional.ofNullable(headers.get("Content-Length")).map(c -> Integer.parseInt(c.get(0))).orElse(-1));

                    if (body.length == httpResponseVO.getBodyIndex()) {
                        body = httpResponseVO.setGetBody(byteExpansion(body));
                    }

                    if (contentLength != -1) {
                        body[httpResponseVO.getIncrementBodyIndex()] = b;
                        if (httpResponseVO.getBodyIndex().equals(contentLength)) {
                            isEnd = true;
                            break;
                        }
                    } else if ("chunked".equals(transferEncoding)) {
                        String chunked = httpResponseVO.getChunked();
                        if (chunked.endsWith("\r\n")) {
                            chunkedNum = Optional.ofNullable(chunkedNum).orElseGet(() -> Integer.parseInt(chunked.replace("\r\n", ""), 16));
                            if (chunkedNum == 0) {
                                isEnd = true;
                                break;
                            }
                            body[httpResponseVO.getIncrementBodyIndex()] = b;
                            if (httpResponseVO.getBodyIndex() - httpResponseVO.getChunkedInitIndex() == chunkedNum) {
                                httpResponseVO.setChunked("");
                                chunkedNum = null;
                            }
                        } else if (!chunked.equals("") || (b != '\r' && b != '\n')) {
                            httpResponseVO.setChunkedInitIndex(httpResponseVO.getBodyIndex());
                            httpResponseVO.setChunked(chunked + new String(new byte[]{b}));
                        }
                    }
                }
            }
            if (isEnd) {
                socketChannel.close();
                contentDecode(httpResponseVO);
                httpResponseVO.getCallBack().accept(httpResponseVO);
                return;
            }
            byteBuffer.clear();
        }
        selectionKey.interestOps(SelectionKey.OP_READ);
    }

    @Override
    public void doGet(String uri, Consumer<HttpResponseVO> callBack, LinkedHashMap... headers) {
        doRequest(uri, "GET", null, callBack, Exception::printStackTrace, headers);
    }

    @Override
    public void doGet(String uri, Consumer<HttpResponseVO> callBack, Consumer<Exception> exceptionHandler, LinkedHashMap... headers) {
        doRequest(uri, "GET", null, callBack, exceptionHandler, headers);
    }

    @Override
    public void doRequest(String uri, String method, byte[] body, Consumer<HttpResponseVO> callBack, Consumer<Exception> exceptionHandler, LinkedHashMap... headers) {
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
            httpRequestVO.setBody(body);
            httpRequestVO.setPort(port);
            httpRequestVO.setMethod(method);
            httpRequestVO.setAddress(address);
            httpRequestVO.setCallBack(callBack);
            httpRequestVO.setPath(uri.substring(pathIndex));
            httpRequestVO.setExceptionHandler(exceptionHandler);
            Optional.ofNullable(headers).filter(h -> h.length > 0).map(h -> h[0]).ifPresent(httpRequestVO::setHeaders);

            SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(address, port));
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_WRITE, httpRequestVO);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将请求转为http请求数组
     *
     * @param httpRequestVO
     * @return
     */
    private ByteBuffer requestByteBuffer(HttpRequestVO httpRequestVO) {
        return Optional.ofNullable(httpRequestVO.getByteBuffer()).orElseGet(() -> {
            try {
                Map<String, Object> headers = Optional.ofNullable(httpRequestVO.getHeaders()).orElseGet(LinkedHashMap::new);
                headers.put("HOST", headers.getOrDefault("HOST", httpRequestVO.getAddress()));

                String requestLine = httpRequestVO.getMethod() + " " + httpRequestVO.getPath() + " HTTP/1.1 \r\n";
                byte[] request = headers.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining("\r\n", requestLine, "\r\n\r\n")).getBytes("UTF-8");
                ByteBuffer item = ByteBuffer.wrap(Optional.ofNullable(httpRequestVO.getBody()).map(b -> byteConcat(request, b)).orElse(request));
                httpRequestVO.setByteBuffer(item);
                return item;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
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
            originHeader = httpResponseVO.setGetOriginHeader(byteExpansion(originHeader));
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
                httpResponseVO.setBody(GzipUtils.uncompress(httpResponseVO.getBody()));
                break;
        }
    }

}
