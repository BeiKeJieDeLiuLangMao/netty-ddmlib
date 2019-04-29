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
    private CountDownLatch done = new CountDownLatch(1);
    private SyncService.FileStat stat = null;

    public StatFileHandler() {
        setCumulator(COMPOSITE_CUMULATOR);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (done.getCount() > 0) {
            if (in.readableBytes() >= STATE_RESULT_LENGTH) {
                ByteBuf statResult = in.readSlice(STATE_RESULT_LENGTH);
                if (isStatHeader(statResult)) {
                    stat = new SyncService.FileStat(
                        statResult.getIntLE(4),
                        statResult.getIntLE(8),
                        statResult.getIntLE(12));
                } else {
                    stat = null;
                }
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
            return stat;
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
    }

    private boolean isStatHeader(ByteBuf statResult) {
        return statResult.getByte(0) == 'S' &&
            statResult.getByte(1) == 'T' &&
            statResult.getByte(2) == 'A' &&
            statResult.getByte(3) == 'T';
    }
}
