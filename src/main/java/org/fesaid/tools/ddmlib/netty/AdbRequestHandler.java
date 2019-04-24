package org.fesaid.tools.ddmlib.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;

import static io.netty.handler.codec.http.HttpConstants.DEFAULT_CHARSET;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class AdbRequestHandler extends MessageToMessageEncoder<String> {

    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, List<Object> out) {
        String resultStr = String.format("%04X%s", msg.length(), msg);
        out.add(Unpooled.wrappedBuffer(resultStr.getBytes(DEFAULT_CHARSET)));
    }
}
