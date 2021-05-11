package com.net.nio.service.impl;

import com.net.nio.service.SslService;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

/**
 * @author caojiancheng
 * @date 2021/5/11
 * @description
 */
public class SslServiceImpl implements SslService {

    private SSLContext sslCtx;

    public SslServiceImpl() {
        try {
            sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(keyManagers(), trustManagers(), null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SSLEngine initSslEngine(String host, int port) throws Exception {
        SSLEngine sslEngine = sslCtx.createSSLEngine(host, port);
        sslEngine.setUseClientMode(true);
        return sslEngine;
    }

    @Override
    public KeyManager[] keyManagers() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        char[] passphrase = "password".toCharArray();
        ks.load(new FileInputStream("src/main/resources/keystore.jks"), passphrase);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);
        return kmf.getKeyManagers();
    }
}
