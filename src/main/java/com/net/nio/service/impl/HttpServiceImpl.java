package com.net.nio.service.impl;

import com.net.nio.model.HttpRequestVO;
import com.net.nio.model.HttpResponseVO;
import com.net.nio.service.HttpService;
import com.net.nio.service.SslService;
import com.net.nio.utils.Assert;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author caojiancheng
 * @date 2021/4/20
 * @description
 */
public class HttpServiceImpl extends HttpNioImpl implements HttpService {


    private Pattern httpPattern = Pattern.compile("http[s]{0,1}://.*");

    public HttpServiceImpl(SslService sslService, ExecutorService threadPool) {
        super(sslService, threadPool);
    }

    @Override
    public void doGet(String uri, Consumer<HttpResponseVO> callBack, LinkedHashMap... headers) {
        doGet(uri, callBack, Exception::printStackTrace, headers);
    }

    @Override
    public void doGet(String uri, Consumer<HttpResponseVO> callBack, Consumer<Exception> exceptionHandler, LinkedHashMap... headers) {
        doRequest(uri, "GET", null, callBack, exceptionHandler, headers);
    }

    @Override
    public void doRequest(String uri, String method, byte[] body, Consumer<HttpResponseVO> callBack, Consumer<Exception> exceptionHandler, LinkedHashMap... headers) {
        try {
            Matcher matcher = httpPattern.matcher(uri);
            Assert.isTrue(matcher.matches(), "uri格式错误");
            int pIndex = uri.indexOf(":");
            String protocol = uri.substring(0, pIndex);
            uri = uri.substring(pIndex + 3);
            uri = uri.contains("/") ? uri : uri + "/";
            int pathIndex = uri.indexOf("/");
            String host = uri.substring(0, pathIndex);
            int portIndex = host.indexOf(":");
            int port = HTTP.equalsIgnoreCase(protocol) ? 80 : 443;
            if (portIndex > -1) {
                port = Integer.parseInt(host.substring(portIndex + 1));
                host = host.substring(0, portIndex);
            }
            HttpRequestVO httpRequestVO = new HttpRequestVO();
            httpRequestVO.setBody(body);
            httpRequestVO.setPort(port);
            httpRequestVO.setHost(host);
            httpRequestVO.setMethod(method);
            httpRequestVO.setProtocol(protocol);
            httpRequestVO.setCallBack(callBack);
            httpRequestVO.setPath(uri.substring(pathIndex));
            httpRequestVO.setExceptionHandler(exceptionHandler);
            Optional.ofNullable(headers).filter(h -> h.length > 0).map(h -> h[0]).ifPresent(httpRequestVO::setHeaders);

            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(host, port));
            socketChannel.register(selector, SelectionKey.OP_CONNECT, httpRequestVO);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
