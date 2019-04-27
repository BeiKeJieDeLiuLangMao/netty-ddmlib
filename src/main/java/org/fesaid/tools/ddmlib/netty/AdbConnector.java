package org.fesaid.tools.ddmlib.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.thread.NamedThreadFactory;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@ChannelHandler.Sharable
@Slf4j
public class AdbConnector extends ChannelDuplexHandler {
    private final AdbNettyConfig config;
    private final Bootstrap bootstrap;

    public AdbConnector(AdbNettyConfig config) {
        this.config = config;
        bootstrap = new Bootstrap()
            .group(new NioEventLoopGroup(config.getEventLoopGroupWorkerThreadSize(),
                new NamedThreadFactory(config.getEventLoopGroupWorkerPrefix(),
                    config.getEventLoopGroupWorkerThreadSize())))
            .channel(NioSocketChannel.class)
            .handler(this)
            .option(NioChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMills())
            .option(NioChannelOption.TCP_NODELAY, Boolean.TRUE)
            .option(NioChannelOption.SO_KEEPALIVE, Boolean.TRUE);
    }

    public AdbConnection connect(InetSocketAddress adbSockAddr, String serialnumber) throws IOException {
        ChannelFuture f = this.bootstrap.connect(adbSockAddr);
        try {
            f.await(config.getConnectTimeoutMills(), TimeUnit.MILLISECONDS);
            if (f.isCancelled()) {
                throw new IOException("connect cancelled, can not connect to component.", f.cause());
            } else if (!f.isSuccess()) {
                throw new IOException("connect failed, can not connect to component.", f.cause());
            } else {
                injectTrafficHandler(f.channel(), serialnumber);
                f.channel().pipeline().remove(this);
                return new AdbConnection(f.channel());
            }
        } catch (Exception e) {
            throw new IOException("can not connect to component.", e);
        }
    }

    private void injectTrafficHandler(Channel channel, String serialNumber) {
        GlobalTrafficShapingHandler globalTrafficHandler =
            config.getTrafficHandlerGetter().getGlobalTrafficHandler();
        if (!Objects.isNull(globalTrafficHandler)) {
            channel.pipeline().addLast(globalTrafficHandler);
        }
        if (!Objects.isNull(serialNumber)) {
            GlobalTrafficShapingHandler deviceTrafficHandler =
                config.getTrafficHandlerGetter().getDeviceTrafficHandler(serialNumber);
            if (!Objects.isNull(deviceTrafficHandler)) {
                channel.pipeline().addLast(deviceTrafficHandler);
            }
        }
    }
}
