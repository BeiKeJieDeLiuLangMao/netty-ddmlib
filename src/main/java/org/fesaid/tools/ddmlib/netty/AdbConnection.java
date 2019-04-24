package org.fesaid.tools.ddmlib.netty;

import io.netty.channel.Channel;
import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import org.fesaid.tools.ddmlib.TimeoutException;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class AdbConnection implements Closeable {

    private Channel channel;

    AdbConnection(Channel channel) {
        this.channel = channel;
        this.channel.pipeline().addLast(new AdbRequestHandler());
    }

    public synchronized AdbRespondHandler sendAndWaitResponse(String message, long timeout, TimeUnit timeUnit) throws TimeoutException {
        AdbRespondHandler adbRespondHandler = new AdbRespondHandler();
        channel.pipeline().addLast(adbRespondHandler);
        try {
            channel.writeAndFlush(message).sync();
            adbRespondHandler.waitRespond(timeout, timeUnit);
            return adbRespondHandler;
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted.", e);
        } finally {
            channel.pipeline().remove(adbRespondHandler);
        }
    }

    public synchronized void send(String message) {
        try {
            channel.writeAndFlush(message).sync();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted.", e);
        }
    }

    @Override
    public void close() {
        channel.close();
    }
}
