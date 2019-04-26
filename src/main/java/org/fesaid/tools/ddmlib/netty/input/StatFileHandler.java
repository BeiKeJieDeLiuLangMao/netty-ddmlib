package org.fesaid.tools.ddmlib.netty.input;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.fesaid.tools.ddmlib.SyncService;
import org.fesaid.tools.ddmlib.TimeoutException;
import org.fesaid.tools.ddmlib.utils.ArrayHelper;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class StatFileHandler extends ByteToMessageDecoder implements AdbInputHandler{

    private static final int STATE_RESULT_LENGTH = 16;
    private byte[] statResult = new byte[STATE_RESULT_LENGTH];
    private CountDownLatch done = new CountDownLatch(1);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (done.getCount() > 0) {
            if (in.readableBytes() >= STATE_RESULT_LENGTH) {
                in.readBytes(statResult, 0, STATE_RESULT_LENGTH);
                done.countDown();
                ctx.pipeline().remove(this);
                if (in.isReadable()) {
                    ctx.fireChannelRead(in);
                }
            }
        }
    }

    public SyncService.FileStat waitData(long timeout, TimeUnit timeUnit) throws TimeoutException {
        try {
            if (timeout > 0) {
                if (!done.await(timeout, timeUnit)) {
                    throw new TimeoutException("Wait data timeout.");
                }
            } else {
                done.await();
            }
            if (isStatHeader()) {
                return new SyncService.FileStat(
                    ArrayHelper.swap32bitFromArray(statResult, 4),
                    ArrayHelper.swap32bitFromArray(statResult, 8),
                    ArrayHelper.swap32bitFromArray(statResult, 12));
            } else {
                return null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
    }

    private boolean isStatHeader() {
        return statResult[0] == 'S' &&
            statResult[1] == 'T' &&
            statResult[2] == 'A' &&
            statResult[3] == 'T';
    }
}
