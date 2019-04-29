package org.fesaid.tools.ddmlib.netty.input;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.fesaid.tools.ddmlib.AdbHelper;
import org.fesaid.tools.ddmlib.SyncException;
import org.fesaid.tools.ddmlib.SyncService;
import org.fesaid.tools.ddmlib.TimeoutException;

import static org.fesaid.tools.ddmlib.SyncException.SyncError.TRANSFER_PROTOCOL_ERROR;
import static org.fesaid.tools.ddmlib.SyncService.HEADER_LENGTH;
import static org.fesaid.tools.ddmlib.SyncService.State.WAIT_ERROR_MESSAGE;
import static org.fesaid.tools.ddmlib.SyncService.State.WAIT_HEADER;
import static org.fesaid.tools.ddmlib.utils.ArrayHelper.swap32bitFromArray;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class PushFileHandler extends ByteToMessageDecoder implements AdbInputHandler {

    private SyncService.State state = WAIT_HEADER;
    private CountDownLatch done = new CountDownLatch(1);
    private boolean success;
    private SyncException cause;
    private int dataLength;

    public PushFileHandler() {
        setCumulator(COMPOSITE_CUMULATOR);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws SyncException {
        switch (state) {
            case WAIT_HEADER:
                if (in.readableBytes() >= HEADER_LENGTH) {
                    handleHeaderRead(in.readSlice(HEADER_LENGTH));
                }
                break;
            case WAIT_ERROR_MESSAGE:
                if (in.readableBytes() >= dataLength) {
                    throw new SyncException(TRANSFER_PROTOCOL_ERROR, in.readSlice(dataLength).toString(AdbHelper.DEFAULT_CHARSET));
                }
                break;
            case WAIT_DATA:
            default:
                throw new RuntimeException("Should never happen.");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        success = false;
        if (cause instanceof SyncException) {
            this.cause = (SyncException) cause;
        } else {
            this.cause = new SyncException(TRANSFER_PROTOCOL_ERROR, cause);
        }
        done.countDown();
    }

    public void waitFinish(long timeout, TimeUnit timeUnit) throws SyncException, TimeoutException {
        try {
            if (!done.await(timeout, timeUnit)) {
                throw new TimeoutException("Wait result timeout.");
            }
            if (!success) {
                throw cause;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleHeaderRead(ByteBuf headerData) throws SyncException {
        if (isOkayHeader(headerData)) {
            success = true;
            done.countDown();
        } else if (isFailHeader(headerData)) {
            state = WAIT_ERROR_MESSAGE;
            dataLength = headerData.getIntLE(4);
        } else {
            throw new SyncException(SyncException.SyncError.TRANSFER_PROTOCOL_ERROR);
        }
    }

    private boolean isOkayHeader(ByteBuf headerData) {
        return headerData.getByte(0) == 'O' &&
            headerData.getByte(1) == 'K' &&
            headerData.getByte(2) == 'A' &&
            headerData.getByte(3) == 'Y';
    }

    private boolean isFailHeader(ByteBuf headerData) {
        return headerData.getByte(0) == 'F' &&
            headerData.getByte(1) == 'A' &&
            headerData.getByte(2) == 'I' &&
            headerData.getByte(3) == 'L';
    }
}
