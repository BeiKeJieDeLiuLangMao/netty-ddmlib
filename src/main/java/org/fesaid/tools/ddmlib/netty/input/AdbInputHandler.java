package org.fesaid.tools.ddmlib.netty.input;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import java.util.Objects;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public interface AdbInputHandler extends ChannelInboundHandler {

    /**
     * common behaviour for unhandled data
     *
     * @param ctx channel handler context
     * @param msg message
     */
    default void unhandledData(ChannelHandlerContext ctx, Object msg) {
        // If this is last handler and handling finished, means connection will been close soon, so eat all left data to
        // avoid "Discarded inbound message" warnings.
        if (!Objects.equals(ctx.pipeline().last(), this)) {
            ctx.fireChannelRead(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }
}
