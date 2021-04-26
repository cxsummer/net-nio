package com.net.nio;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.IntStream;

/**
 * @author caojiancheng
 * @date 2021/4/20
 * @description
 */
@Slf4j
public class NetNioApplication {
    static Selector selector;

    public static void main(String[] args) {
        try {
            selector = Selector.open();
            SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress("www.jsons.cn", 80));
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_WRITE);
            System.out.println(selector.keys().size());
            selector.select(100);
            socketChannel.close();
            System.out.println(selector.keys().size());
            selector.select(100);
            System.out.println(selector.keys().size());

            /*while (selector.select() > 0) {
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();
                    if (selectionKey.isWritable()) {
                        SocketChannel socketChannelItem = (SocketChannel) selectionKey.channel();
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("GET /urlencode HTTP/1.1 \r\n");
                        stringBuilder.append("HOST:www.jsons.cn\r\n");
                        stringBuilder.append("\r\n");
                        ByteBuffer byteBuffer = ByteBuffer.wrap(stringBuilder.toString().getBytes());
                        socketChannelItem.write(byteBuffer);
                        selectionKey.interestOps(SelectionKey.OP_READ);
                    } else if (selectionKey.isReadable()) {
                        SocketChannel socketChannelItem = (SocketChannel) selectionKey.channel();
                        ByteBuffer byteBuffer = ByteBuffer.allocate(10240);
                        while (socketChannelItem.read(byteBuffer) > 0) {
                            byteBuffer.flip();
                            byte[] b = new byte[byteBuffer.limit()];
                            byteBuffer.get(b);
                            System.out.print(new String(b, "utf-8"));
                            if(new String(b, "utf-8").endsWith("\r\n\r\n")){
                                System.out.println("结束");
                                socketChannelItem.close();
                                break;
                            }
                            byteBuffer.clear();
                        }
                    }
                }
            }*/
        } catch (IOException e) {
            log.error("构建SocketChannel失败", e);
            throw new RuntimeException(e);
        }

        screenShotAsFile(0,0,1905,1076,"/Users/user/parasite","a","jpg");
    }

    /**
     * 指定屏幕区域截图，返回截图的BufferedImage对象
     * @param x
     * @param y
     * @param width
     * @param height
     * @return
     */
    public BufferedImage getScreenShot(int x, int y, int width, int height) {
        BufferedImage bfImage = null;
        try {
            Robot robot = new Robot();
            bfImage = robot.createScreenCapture(new Rectangle(x, y, width, height));
        } catch (AWTException e) {
            e.printStackTrace();
        }
        return bfImage;
    }

    /**
     * 指定屏幕区域截图，保存到指定目录
     * @param x
     * @param y
     * @param width
     * @param height
     * @param savePath - 文件保存路径
     * @param fileName - 文件保存名称
     * @param format - 文件格式
     */
    public static void screenShotAsFile(int x, int y, int width, int height, String savePath, String fileName, String format) {
        try {
            Robot robot = new Robot();
            BufferedImage bfImage = robot.createScreenCapture(new Rectangle(x, y, width, height));
            File path = new File(savePath);
            File file = new File(path, fileName+ "." + format);
            ImageIO.write(bfImage, format, file);
        } catch (AWTException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void doRead(SocketChannel socketChannel) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(10240);
            Integer readLength = 0;
            Integer contentLength = null;
            while (socketChannel.read(byteBuffer) > -1) {
                byteBuffer.flip();
                if (byteBuffer.limit() == 0) {
                    break;
                }
                if (contentLength != null) {
                    readLength += byteBuffer.limit();
                }

                byte[] b = new byte[byteBuffer.limit()];
                IntStream.range(0, b.length).forEach(i -> b[i] = byteBuffer.get(i));
                String resp = new String(b, "utf-8");
                String[] respValue = resp.split("\r\n");
                contentLength = contentLength != null ? contentLength :
                        Arrays.stream(respValue).filter(r -> r.contains("Content-Length"))
                                .map(r -> r.split(":")[1].trim()).map(Integer::new).findFirst().get();
                log.info(resp);
                if (readLength.equals(contentLength)) {
                    break;
                }
                byteBuffer.clear();
            }
            socketChannel.close();
        } catch (Exception e) {
            log.error("读取http nio失败", e);
        }
    }

    private static Charset charset = Charset.forName("UTF8");

    public static void doWrite(SocketChannel socketChannel) {
        try {
            String line = "GET / HTTP/1.1 \r\n";
            line += "\r\n";
            socketChannel.write(charset.encode(line));
            socketChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            log.error("写入http nio失败", e);
        }
    }


    public static void handleHttpNio() {
        try {
            int size = selector.keys().size();
            while (!Thread.currentThread().isInterrupted()) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    iterator.remove();
                    if (selectionKey.isReadable()) {
                        doRead(socketChannel);
                    } else if (selectionKey.isWritable()) {
                        doWrite(socketChannel);
                    }
                }
            }
            log.info("http nio处理中断");
        } catch (Exception e) {
            log.error("处理http nio失败", e);
        }

    }
}
