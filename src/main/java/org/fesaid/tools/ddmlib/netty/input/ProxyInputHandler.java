package org.fesaid.tools.ddmlib.netty.input;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@Slf4j
public class ProxyInputHandler extends ByteToMessageDecoder implements AdbInputHandler {

    private ChannelHandlerContext proxyConnectionCtx;
    private String serialNumber;

    public ProxyInputHandler(ChannelHandlerContext proxyConnectionCtx, String serialNumber) {
        this.proxyConnectionCtx = proxyConnectionCtx;
        this.serialNumber = serialNumber;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        proxyConnectionCtx.writeAndFlush(in.readBytes(in.readableBytes()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (proxyConnectionCtx.channel().isActive()) {
            proxyConnectionCtx.close();
            log.info("Adb-Proxy {}-{}: Closed, reason: original adb connection is closed",
                proxyConnectionCtx.channel().id(), serialNumber);
        }
        ctx.close();
    }

}
