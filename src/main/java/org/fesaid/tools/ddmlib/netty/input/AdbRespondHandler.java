package org.fesaid.tools.ddmlib.netty.input;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.AdbHelper;
import org.fesaid.tools.ddmlib.TimeoutException;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@Slf4j
public class AdbRespondHandler extends ChannelInboundHandlerAdapter implements AdbInputHandler {

    private static final int OKAY_SIZE = 4;
    private static final int MESSAGE_LENGTH = 4;
    private CountDownLatch respondCountDown = new CountDownLatch(1);
    @Getter
    private Boolean okay;
    @Getter
    private String message;
    private Integer messageLength;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (respondCountDown.getCount() > 0) {
            ByteBuf byteBuf = (ByteBuf) msg;
            if (Objects.isNull(okay)) {
                if (byteBuf.readableBytes() >= OKAY_SIZE) {
                    ByteBuf okData = byteBuf.readSlice(OKAY_SIZE);
                    okay = okData.getByte(0) == (byte) 'O' &&
                        okData.getByte(1) == (byte) 'K' &&
                        okData.getByte(2) == (byte) 'A' &&
                        okData.getByte(3) == (byte) 'Y';
                    channelRead(ctx, msg);
                }
            } else {
                if (okay) {
                    finish(ctx, msg);
                } else {
                    if (Objects.isNull(messageLength)) {
                        if (byteBuf.readableBytes() >= MESSAGE_LENGTH) {
                            String lengthString = byteBuf.readSlice(MESSAGE_LENGTH).toString(AdbHelper.DEFAULT_CHARSET);
                            try {
                                messageLength = Integer.parseInt(lengthString, 16);
                                channelRead(ctx, msg);
                            } catch (Exception e) {
                                message = lengthString + byteBuf.readSlice(byteBuf.readableBytes())
                                    .toString(AdbHelper.DEFAULT_CHARSET);
                                finish(ctx, msg);
                            }
                        }
                    } else {
                        if (byteBuf.readableBytes() >= messageLength) {
                            message = byteBuf.readSlice(messageLength).toString(AdbHelper.DEFAULT_CHARSET);
                            finish(ctx, msg);
                        }
                    }
                }
            }
        } else {
            unhandledData(ctx, msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        okay = false;
        message = cause.getMessage();
        respondCountDown.countDown();
    }

    private void finish(ChannelHandlerContext ctx, Object msg) {
        respondCountDown.countDown();
        unhandledData(ctx, msg);
    }

    public void waitRespond(long timeout, TimeUnit timeUnit) throws TimeoutException {
        try {
            if (timeout > 0) {
                if (!respondCountDown.await(timeout, timeUnit)) {
                    throw new TimeoutException("Wait response timeout.");
                }
            } else {
                respondCountDown.await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
    }
}
