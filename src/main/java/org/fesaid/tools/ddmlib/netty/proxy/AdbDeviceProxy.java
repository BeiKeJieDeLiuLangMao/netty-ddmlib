package org.fesaid.tools.ddmlib.netty.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.DdmPreferences;
import org.fesaid.tools.ddmlib.netty.AdbNettyConfig;
import org.fesaid.tools.ddmlib.thread.NamedThreadFactory;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@Slf4j
@ChannelHandler.Sharable
public class AdbDeviceProxy extends ChannelInitializer {

    private volatile static AdbDeviceProxy INSTANCE;
    private final EventLoopGroup eventLoopGroupBoss;
    private final EventLoopGroup eventLoopGroupWorker;
    private final ServerBootstrap bootstrap = new ServerBootstrap();

    private AdbDeviceProxy(AdbNettyConfig adbNettyConfig) {
        try {
            eventLoopGroupWorker = new NioEventLoopGroup(adbNettyConfig.getEventLoopGroupWorkerThreadSize(),
                new NamedThreadFactory(adbNettyConfig.getProxyEventLoopGroupWorkerPrefix(),
                    adbNettyConfig.getEventLoopGroupWorkerThreadSize()));
            eventLoopGroupBoss = new NioEventLoopGroup(adbNettyConfig.getEventExecutorGroupThreadSize(),
                new NamedThreadFactory(adbNettyConfig.getEventExecutorGroupPrefix(),
                    adbNettyConfig.getEventExecutorGroupThreadSize()));
        } catch (Exception e) {
            throw new RuntimeException("Adb proxy event loop groups instantiation failed", e);
        }
        init();
    }

    @Override
    protected void initChannel(Channel ch) {
        ch.pipeline().addFirst(new ConnectionProxyHandler());
    }

    public static void start(AdbNettyConfig adbNettyConfig) {
        if (INSTANCE == null) {
            synchronized (AdbDeviceProxy.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AdbDeviceProxy(adbNettyConfig);
                }
            }
        }
    }

    private void init() {
        log.info("Adb-Proxy: Initializing...");
        bootstrap
            .group(eventLoopGroupBoss, eventLoopGroupWorker)
            .channel(NioServerSocketChannel.class)
            .childHandler(this);
        try {
            ChannelFuture bindFuture = bootstrap.bind(DdmPreferences.getAdbProxyPort()).sync();
            if (!bindFuture.isSuccess()) {
                throw new RuntimeException("Adb proxy port binding failed", bindFuture.cause());
            } else {
                log.info("Adb-Proxy: Server started at port: {}", DdmPreferences.getAdbProxyPort());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Adb proxy port binding interrupted", e);
        }
    }

    public void stop() {
        eventLoopGroupBoss.shutdownGracefully();
        eventLoopGroupWorker.shutdownGracefully();
    }
}
