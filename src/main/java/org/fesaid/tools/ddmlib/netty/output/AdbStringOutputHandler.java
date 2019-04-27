package org.fesaid.tools.ddmlib.netty.output;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import org.fesaid.tools.ddmlib.AdbHelper;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class AdbStringOutputHandler extends MessageToMessageEncoder<String> implements AdbOutputHandler<String> {

    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, List<Object> out) {
        String resultStr = String.format("%04X%s", msg.length(), msg);
        out.add(Unpooled.wrappedBuffer(resultStr.getBytes(AdbHelper.DEFAULT_CHARSET)));
    }
}
