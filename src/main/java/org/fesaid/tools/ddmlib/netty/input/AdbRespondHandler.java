package org.fesaid.tools.ddmlib.netty.input;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.TimeoutException;

import static io.netty.handler.codec.http.HttpConstants.DEFAULT_CHARSET;

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
                    okay = byteBuf.readByte() == (byte) 'O' &&
                        byteBuf.readByte() == (byte) 'K' &&
                        byteBuf.readByte() == (byte) 'A' &&
                        byteBuf.readByte() == (byte) 'Y';
                    channelRead(ctx, msg);
                }
            } else {
                if (okay) {
                    finishRead(ctx, msg);
                } else {
                    if (Objects.isNull(messageLength)) {
                        if (byteBuf.readableBytes() >= MESSAGE_LENGTH) {
                            String lengthString = byteBuf.readBytes(MESSAGE_LENGTH).toString(DEFAULT_CHARSET);
                            try {
                                messageLength = Integer.parseInt(lengthString, 16);
                                channelRead(ctx, msg);
                            } catch (Exception e) {
                                message = lengthString + byteBuf.readBytes(byteBuf.readableBytes()).toString(DEFAULT_CHARSET);
                                finishRead(ctx, msg);
                            }
                        }
                    } else {
                        if (byteBuf.readableBytes() >= messageLength) {
                            message = byteBuf.readBytes(messageLength).toString(DEFAULT_CHARSET);
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

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (respondCountDown.getCount() > 0) {
            okay = false;
            message = "Connection inactive";
            respondCountDown.countDown();
        }
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
