package org.fesaid.tools.ddmlib.netty.input;

import com.google.common.collect.Maps;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fesaid.tools.ddmlib.AdbHelper;
import org.fesaid.tools.ddmlib.IDevice;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class DeviceMonitorHandler extends ByteToMessageDecoder implements AdbInputHandler {
    private static final int LENGTH_FIELD_SIZE = 4;
    private final byte[] mLengthBuffer = new byte[LENGTH_FIELD_SIZE];
    private int length;
    private boolean needLengthData = true;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (needLengthData) {
            if (in.readableBytes() >= LENGTH_FIELD_SIZE) {
                in.readBytes(mLengthBuffer, 0, LENGTH_FIELD_SIZE);
                length = Integer.parseInt(new String(mLengthBuffer, AdbHelper.DEFAULT_CHARSET), 16);
                if (length <= 0) {
                    out.add(new HashMap<String, IDevice.DeviceState>(0));
                } else {
                    needLengthData = false;
                }
            }
        } else {
            Map<String, IDevice.DeviceState> deviceStateMap = Maps.newHashMap();
            if (in.readableBytes() >= length) {
                byte[] data = new byte[length];
                in.readBytes(data, 0, length);
                String result = new String(data, AdbHelper.DEFAULT_CHARSET);
                String[] devices = result.split("\n");
                for (String d : devices) {
                    String[] param = d.split("\t");
                    if (param.length == 2) {
                        // new adb uses only serial numbers to identify devices
                        deviceStateMap.put(param[0], IDevice.DeviceState.getState(param[1]));
                    }
                }
            }
            out.add(deviceStateMap);
            needLengthData = true;
        }
    }
}