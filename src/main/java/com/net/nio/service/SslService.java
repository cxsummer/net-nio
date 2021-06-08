package com.net.nio.service;

import com.net.nio.model.HttpRequestVO;

import javax.net.ssl.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStoreException;
import java.util.concurrent.ExecutorService;

/**
 * @author caojiancheng
 * @date 2021/5/11
 * @description
 */
public interface SslService {

    /**
     * 初始化SSLEngine
     *
     * @param host 地址
     * @param port 端口
     * @return sslEngine
     * @throws Exception
     */
    SSLEngine initSslEngine(String host, int port) throws Exception;

    /**
     * 本地证书和私钥
     *
     * @return keyManager
     * @throws Exception
     */
    default KeyManager[] keyManagers() throws Exception {
        return null;
    }

    /**
     * 受信任的证书
     *
     * @return keyManager
     * @throws Exception
     */
    default TrustManager[] trustManagers() throws Exception {
        return null;
    }

    /**
     * ssl握手
     *
     * @param sslEngine
     * @param socketChannel
     * @throws Exception
     */
    default void sslHandshake(SSLEngine sslEngine, SocketChannel socketChannel) throws Exception {
        sslEngine.beginHandshake();
        SSLSession sslSession = sslEngine.getSession();
        SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
        ByteBuffer appBuffer = ByteBuffer.allocate(sslSession.getApplicationBufferSize());
        ByteBuffer packetBuffer = ByteBuffer.allocate(sslSession.getPacketBufferSize());

        ByteBuffer appWBuffer = ByteBuffer.allocate(sslSession.getApplicationBufferSize());
        ByteBuffer packetWBuffer = ByteBuffer.allocate(sslSession.getPacketBufferSize());

        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (handshakeStatus) {
                case NEED_UNWRAP:
                    socketChannel.read(packetBuffer);
                    packetBuffer.flip();
                    SSLEngineResult res = sslEngine.unwrap(packetBuffer, appBuffer);
                    packetBuffer.compact();
                    handshakeStatus = res.getHandshakeStatus();
                    break;
                case NEED_WRAP:
                    packetWBuffer.clear();
                    res = sslEngine.wrap(appWBuffer, packetWBuffer);
                    handshakeStatus = res.getHandshakeStatus();
                    if (res.getStatus() == SSLEngineResult.Status.OK) {
                        packetWBuffer.flip();
                        while (packetWBuffer.hasRemaining()) {
                            socketChannel.write(packetWBuffer);
                        }
                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    handshakeStatus = sslEngine.getHandshakeStatus();
                    break;
            }
        }
    }
}
