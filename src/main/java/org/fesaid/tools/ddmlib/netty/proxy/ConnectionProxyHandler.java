package org.fesaid.tools.ddmlib.netty.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.AdbHelper;
import org.fesaid.tools.ddmlib.AndroidDebugBridge;
import org.fesaid.tools.ddmlib.DdmPreferences;
import org.fesaid.tools.ddmlib.netty.AdbConnection;
import org.fesaid.tools.ddmlib.netty.input.DeviceMonitorHandler;

import static org.fesaid.tools.ddmlib.DeviceMonitor.ADB_TRACK_DEVICES_COMMAND;
import static org.fesaid.tools.ddmlib.SyncService.ID_OKAY;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@Slf4j
public class ConnectionProxyHandler extends ByteToMessageDecoder {

    private static final int LENGTH_FIELD_SIZE = 4;
    private static final String SPECIFIC_DEVICE_TRANSPORT_HEADER = "host:transport:";
    private static final String FORWARD_HEADER = "host-serial:";
    private Integer headerLength;
    private String header;
    private AdbConnection adbConnection;
    private String serialNumber = "NULL";
    private boolean originalConnectionHeaderSent = false;

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (adbConnection != null && adbConnection.isActive()) {
            log.info("Adb-Proxy {}-{}: Closed, reason: proxy connection closed", ctx.channel().id(), serialNumber);
            adbConnection.close();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (header == null) {
            if (headerLength == null) {
                if (in.readableBytes() >= LENGTH_FIELD_SIZE) {
                    headerLength = Integer.parseInt(in.readSlice(LENGTH_FIELD_SIZE).toString(AdbHelper.DEFAULT_CHARSET), 16);
                }
            } else if (in.readableBytes() >= headerLength) {
                header = in.readSlice(headerLength).toString(AdbHelper.DEFAULT_CHARSET);
            }
        }
        if (header != null) {
            if (header.equals(ADB_TRACK_DEVICES_COMMAND)) {
                handleTrackDevices(ctx, in);
            } else if (header.startsWith(SPECIFIC_DEVICE_TRANSPORT_HEADER)) {
                handleTransport(header.replace(SPECIFIC_DEVICE_TRANSPORT_HEADER, ""), ctx, in);
            } else if (header.startsWith(FORWARD_HEADER)) {
                handleTransport(header.split(":")[1], ctx, in);
            } else {
                log.error("Adb-Proxy {}-{}: Closed, reason: header type not supported yet, {}",
                    ctx.channel().id(), serialNumber, header);
                ctx.close();
            }
        }
    }

    private void handleTransport(String serialNumber, ChannelHandlerContext ctx, ByteBuf in) {
        this.serialNumber = serialNumber;
        if (DdmPreferences.shouldOpenAdbProxy(serialNumber)) {
            buildProxy(ctx, in);
        } else {
            log.info("Adb-Proxy {}-{}: Closed, reason: want to use limited device", ctx.channel().id(), serialNumber);
            ctx.close();
        }
    }

    private void handleTrackDevices(ChannelHandlerContext ctx, ByteBuf in) {
        if (createProxyConnectionSuccess(ctx)) {
            try {
                if (!originalConnectionHeaderSent) {
                    // 先发OKAY，保证OKAY在device list之前发送，如果出错了直接断开就可以接受
                    ctx.writeAndFlush(Unpooled.wrappedBuffer(ID_OKAY));
                    adbConnection.sendAndWaitSuccess(
                        header,
                        DdmPreferences.getTimeOut(),
                        TimeUnit.MILLISECONDS,
                        new DeviceMonitorHandler(),
                        new TrackDevicesFilterHandler(ctx));
                    originalConnectionHeaderSent = true;
                }
                if (in.readableBytes() > 0) {
                    adbConnection.writeAndFlush(in.readBytes(in.readableBytes()));
                }
            } catch (Exception e) {
                log.info("Adb-Proxy {}-{}: Closed, reason: {}", ctx.channel().id(), serialNumber, e.getMessage());
                ctx.close();
            }
        }
    }

    private void buildProxy(ChannelHandlerContext ctx, ByteBuf in) {
        if (createProxyConnectionSuccess(ctx)) {
            adbConnection.buildProxyConnectionIfNecessary(ctx, serialNumber);
            if (!originalConnectionHeaderSent) {
                adbConnection.writeAndFlush(Unpooled.wrappedBuffer(String.format("%04X%s", headerLength, header)
                    .getBytes(AdbHelper.DEFAULT_CHARSET)));
                originalConnectionHeaderSent = true;
            }
            if (in.readableBytes() > 0) {
                adbConnection.writeAndFlush(in.readBytes(in.readableBytes()));
            }
        }
    }

    private boolean createProxyConnectionSuccess(ChannelHandlerContext ctx) {
        if (adbConnection == null) {
            try {
                adbConnection = AdbHelper.connect(AndroidDebugBridge.getSocketAddress(), null);
                log.info("Adb-Proxy {}-{}: Opened, command: {}", ctx.channel().id(),
                    serialNumber, header);
                return true;
            } catch (IOException e) {
                log.info("Adb-Proxy {}-{}: Closed, reason: create original adb connection failed", ctx.channel().id(),
                    serialNumber);
                ctx.close();
                return false;
            }
        } else {
            return true;
        }
    }
}
