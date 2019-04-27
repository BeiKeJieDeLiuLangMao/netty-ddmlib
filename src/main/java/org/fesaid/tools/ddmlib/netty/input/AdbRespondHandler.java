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
                    ByteBuf okData = byteBuf.readBytes(OKAY_SIZE);
                    okay = okData.readByte() == (byte) 'O' &&
                        okData.readByte() == (byte) 'K' &&
                        okData.readByte() == (byte) 'A' &&
                        okData.readByte() == (byte) 'Y';
                    ReferenceCountUtil.release(okData);
                    channelRead(ctx, msg);
                }
            } else {
                if (okay) {
                    finishRead(ctx, msg);
                } else {
                    if (Objects.isNull(messageLength)) {
                        if (byteBuf.readableBytes() >= MESSAGE_LENGTH) {
                            ByteBuf lengthData = byteBuf.readBytes(MESSAGE_LENGTH);
                            String lengthString = lengthData.toString(AdbHelper.DEFAULT_CHARSET);
                            ReferenceCountUtil.release(lengthData);
                            try {
                                messageLength = Integer.parseInt(lengthString, 16);
                                channelRead(ctx, msg);
                            } catch (Exception e) {
                                ByteBuf leftData = byteBuf.readBytes(byteBuf.readableBytes());
                                message = lengthString +leftData.toString(AdbHelper.DEFAULT_CHARSET);
                                ReferenceCountUtil.release(leftData);
                                finishRead(ctx, msg);
                            }
                        }
                    } else {
                        if (byteBuf.readableBytes() >= messageLength) {
                            ByteBuf messageData = byteBuf.readBytes(messageLength);
                            message = messageData.toString(AdbHelper.DEFAULT_CHARSET);
                            ReferenceCountUtil.release(messageData);
                            finishRead(ctx, msg);
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

    private void finishRead(ChannelHandlerContext ctx, Object msg) {
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
