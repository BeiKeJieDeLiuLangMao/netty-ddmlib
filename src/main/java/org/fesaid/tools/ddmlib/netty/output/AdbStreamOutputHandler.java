package org.fesaid.tools.ddmlib.netty.output;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.stream.ChunkedStream;
import java.io.InputStream;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class AdbStreamOutputHandler extends ChannelOutboundHandlerAdapter implements AdbOutputHandler<InputStream> {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        try {
            ChunkedStream chunkedStream = new ChunkedStream((InputStream) msg);
            ByteBuf byteBuf;
            while ((byteBuf = chunkedStream.readChunk(ctx.alloc())) != null) {
                ctx.writeAndFlush(byteBuf, promise);
            }
        } catch (Exception e) {
            promise.setFailure(e);
        }
    }
}
