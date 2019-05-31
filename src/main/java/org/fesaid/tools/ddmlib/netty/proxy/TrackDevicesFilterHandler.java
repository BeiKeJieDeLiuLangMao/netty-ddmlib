package org.fesaid.tools.ddmlib.netty.proxy;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.AdbHelper;
import org.fesaid.tools.ddmlib.DdmPreferences;
import org.fesaid.tools.ddmlib.IDevice;
import org.fesaid.tools.ddmlib.netty.input.AdbInputHandler;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@Slf4j
class TrackDevicesFilterHandler extends ChannelInboundHandlerAdapter implements AdbInputHandler {
    private ChannelHandlerContext proxyConnectionCtx;

    TrackDevicesFilterHandler(ChannelHandlerContext proxyConnectionCtx) {
        this.proxyConnectionCtx = proxyConnectionCtx;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Map<String, IDevice.DeviceState> stateMap = ((Map<String, IDevice.DeviceState>) msg).entrySet().stream()
            .filter(entry -> DdmPreferences.shouldOpenAdbProxy(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (stateMap.size() == 0) {
            proxyConnectionCtx.writeAndFlush(Unpooled.wrappedBuffer(String.format("%04X%s", 0, "")
                .getBytes(AdbHelper.DEFAULT_CHARSET)));
        } else {
            StringBuilder dataBuilder = new StringBuilder();
            stateMap.forEach((key, value) ->
                dataBuilder.append(key)
                    .append("\t")
                    .append(value.getState())
                    .append("\n"));
            proxyConnectionCtx.writeAndFlush(Unpooled.wrappedBuffer(String.format("%04X%s", dataBuilder.toString().length(),
                dataBuilder.toString()).getBytes(AdbHelper.DEFAULT_CHARSET)));
        }
    }
}
