package org.fesaid.tools.ddmlib.netty.input;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.IShellOutputReceiver;
import org.fesaid.tools.ddmlib.ShellCommandUnresponsiveException;
import org.fesaid.tools.ddmlib.TimeoutException;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@Slf4j
public class AdbStreamInputHandler extends ChannelInboundHandlerAdapter implements AdbInputHandler {

    private static final int BUFFER_SIZE = 1024;
    private IShellOutputReceiver receiver;
    private CountDownLatch respondBeginCountDown = new CountDownLatch(1);
    private CountDownLatch finishCountDown = new CountDownLatch(1);
    private byte[] response = new byte[BUFFER_SIZE];

    public AdbStreamInputHandler(IShellOutputReceiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf) {
            if (respondBeginCountDown.getCount() > 0) {
                respondBeginCountDown.countDown();
            }
            if (finishCountDown.getCount() > 0) {
                if (receiver.isCancelled()) {
                    finishCountDown.countDown();
                } else {
                    while (((ByteBuf) msg).readableBytes() > 0) {
                        int length = Math.min(((ByteBuf) msg).readableBytes(), BUFFER_SIZE);
                        ((ByteBuf) msg).readBytes(response, 0, length);
                        receiver.addOutput(response, 0, length);
                        if (receiver.isCancelled()) {
                            finishCountDown.countDown();
                            break;
                        }
                    }
                }
            } else {
                unhandledData(ctx, msg);
            }
        } else {
            unhandledData(ctx, msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        receiver.flush();
        finishCountDown.countDown();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        receiver.flush();
        finishCountDown.countDown();
    }

    public void waitResponseBegin(long timeout, TimeUnit timeUnit) throws ShellCommandUnresponsiveException {
        try {
            if (timeout > 0) {
                if (!respondBeginCountDown.await(timeout, timeUnit)) {
                    throw new ShellCommandUnresponsiveException();
                }
            } else {
                respondBeginCountDown.await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
    }

    public void waitFinish(long timeout, TimeUnit timeUnit) throws TimeoutException {
        try {
            if (timeout > 0) {
                if (!finishCountDown.await(timeout, timeUnit)) {
                    throw new TimeoutException("executeRemoteCommand timed out after, " + timeUnit.toMillis(timeout));
                }
            } else {
                finishCountDown.await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
    }
}
