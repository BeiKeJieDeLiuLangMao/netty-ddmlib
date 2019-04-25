package org.fesaid.tools.ddmlib.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.io.Closeable;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.fesaid.tools.ddmlib.AdbCommandRejectedException;
import org.fesaid.tools.ddmlib.TimeoutException;
import org.fesaid.tools.ddmlib.netty.input.AdbInputHandler;
import org.fesaid.tools.ddmlib.netty.input.AdbRespondHandler;
import org.fesaid.tools.ddmlib.netty.output.AdbOutputHandler;
import org.fesaid.tools.ddmlib.netty.output.AdbStreamOutputHandler;
import org.fesaid.tools.ddmlib.netty.output.AdbStringOutputHandler;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class AdbConnection implements Closeable {

    private Channel channel;

    AdbConnection(Channel channel) {
        this.channel = channel;
    }

    public synchronized void sendAndWaitSuccess(String message, long timeout,
        TimeUnit timeUnit, AdbInputHandler... nextHandlers) throws TimeoutException, AdbCommandRejectedException {
        AdbRespondHandler adbRespondHandler = new AdbRespondHandler();
        channel.pipeline().addLast(adbRespondHandler);
        if (nextHandlers != null && nextHandlers.length > 0) {
            channel.pipeline().addLast(nextHandlers);
        }
        send(message, timeout, timeUnit);
        adbRespondHandler.waitRespond(timeout, timeUnit);
        if (!adbRespondHandler.getOkay()) {
            throw new AdbCommandRejectedException(adbRespondHandler.getMessage());
        }
    }

    public synchronized void send(String message, long timeout, TimeUnit timeUnit) throws TimeoutException,
        AdbCommandRejectedException {
        doSend(message, timeout, timeUnit, new AdbStringOutputHandler());
    }

    public synchronized void send(InputStream inputStream) throws TimeoutException,
        AdbCommandRejectedException {
        doSend(inputStream, 0, null, new AdbStreamOutputHandler());
    }

    private <T> void doSend(T message, long timeout, TimeUnit timeUnit,
        AdbOutputHandler<T> adbStringOutputHandler) throws TimeoutException, AdbCommandRejectedException {
        channel.pipeline().addLast(adbStringOutputHandler);
        try {
            ChannelFuture channelFuture = channel.writeAndFlush(message);
            if (timeout > 0) {
                if (!channelFuture.await(timeout, timeUnit)) {
                    channelFuture.cancel(true);
                    throw new TimeoutException("Send request timeout.");
                }
            } else {
                channelFuture.await();
            }
            if (!channelFuture.isSuccess()) {
                throw new AdbCommandRejectedException("Send data failed, " +
                    (Objects.isNull(channelFuture.cause()) ? "" : channelFuture.cause().getMessage()));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted.", e);
        } finally {
            channel.pipeline().remove(adbStringOutputHandler);
        }
    }

    @Override
    public void close() {
        channel.close();
    }
}
