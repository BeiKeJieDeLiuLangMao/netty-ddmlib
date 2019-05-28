package org.fesaid.tools.ddmlib.netty.input;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.fesaid.tools.ddmlib.AdbHelper;
import org.fesaid.tools.ddmlib.SyncException;
import org.fesaid.tools.ddmlib.SyncService;
import org.fesaid.tools.ddmlib.TimeoutException;

import static org.fesaid.tools.ddmlib.SyncException.SyncError.TRANSFER_PROTOCOL_ERROR;
import static org.fesaid.tools.ddmlib.SyncService.HEADER_LENGTH;
import static org.fesaid.tools.ddmlib.SyncService.SYNC_DATA_MAX;
import static org.fesaid.tools.ddmlib.SyncService.State.WAIT_DATA;
import static org.fesaid.tools.ddmlib.SyncService.State.WAIT_ERROR_MESSAGE;
import static org.fesaid.tools.ddmlib.SyncService.State.WAIT_HEADER;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class SameFileCheckHandler extends ByteToMessageDecoder implements AdbInputHandler {

    private CountDownLatch respondBegin = new CountDownLatch(1);
    private CountDownLatch done = new CountDownLatch(1);
    private SyncService.ISyncProgressMonitor monitor;
    private InputStream localStream;
    private SyncService.State state = WAIT_HEADER;
    private int dataLength;
    private Boolean same;
    private byte[] buffer;

    public SameFileCheckHandler(SyncService.ISyncProgressMonitor monitor, InputStream localStream) {
        this.monitor = monitor;
        this.localStream = localStream;
        setCumulator(COMPOSITE_CUMULATOR);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (monitor.isCanceled()) {
            throw new SyncException(SyncException.SyncError.CANCELED);
        }
        switch (state) {
            case WAIT_HEADER:
                if (in.readableBytes() >= HEADER_LENGTH) {
                    handleHeaderRead(in.readSlice(HEADER_LENGTH));
                }
                break;
            case WAIT_DATA:
                if (in.readableBytes() >= dataLength) {
                    handleCompareData(in.nioBuffer(in.readerIndex(), dataLength));
                    in.readerIndex(in.readerIndex() + dataLength);
                    monitor.advance(dataLength);
                    state = WAIT_HEADER;
                }
                break;
            case WAIT_ERROR_MESSAGE:
                if (in.readableBytes() >= dataLength) {
                    throw new SyncException(TRANSFER_PROTOCOL_ERROR, in.readSlice(dataLength).toString(AdbHelper.DEFAULT_CHARSET));
                }
                break;
            default:
                throw new RuntimeException("Should never happen.");
        }
    }

    private void handleHeaderRead(ByteBuf headerData) throws SyncException {
        if (respondBegin.getCount() > 0) {
            if (!isDataHeader(headerData) && !isDoneHeader(headerData) && !isFailHeader(headerData)) {
                throw new SyncException(TRANSFER_PROTOCOL_ERROR);
            } else {
                respondBegin.countDown();
            }
        }
        if (isDataHeader(headerData)) {
            state = WAIT_DATA;
            dataLength = headerData.getIntLE(4);
            if (dataLength > SYNC_DATA_MAX) {
                // buffer overrun!
                // error and exit
                throw new SyncException(SyncException.SyncError.BUFFER_OVERRUN);
            }
        } else if (isDoneHeader(headerData)) {
            finished();
        } else if (isFailHeader(headerData)) {
            state = WAIT_ERROR_MESSAGE;
            dataLength = headerData.getIntLE(4);
        } else {
            throw new SyncException(TRANSFER_PROTOCOL_ERROR);
        }
    }

    private void handleCompareData(ByteBuffer in) throws Exception {
        if (buffer == null) {
            buffer = new byte[1024];
        }
        while (in.remaining() > 0) {
            int readLength = localStream.read(buffer, 0, Math.min(in.remaining(), 1024));
            for (int i = 0; i < readLength; i++) {
                if (buffer[i] != in.get()) {
                    throw new Exception("Not Same");
                }
            }
        }
    }

    private void finished() {
        if (same == null) {
            same = true;
        }
        done.countDown();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        same = false;
        respondBegin.countDown();
        done.countDown();
    }

    public void waitRespondBegin(int out, TimeUnit milliseconds) throws TimeoutException {
        try {
            if (!respondBegin.await(out, milliseconds)) {
                throw new TimeoutException("wait result timeout.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean waitFinish() {
        try {
            done.await();
            return same;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isDataHeader(ByteBuf headerData) {
        return headerData.getByte(0) == 'D' &&
            headerData.getByte(1) == 'A' &&
            headerData.getByte(2) == 'T' &&
            headerData.getByte(3) == 'A';
    }

    private boolean isDoneHeader(ByteBuf headerData) {
        return headerData.getByte(0) == 'D' &&
            headerData.getByte(1) == 'O' &&
            headerData.getByte(2) == 'N' &&
            headerData.getByte(3) == 'E';
    }

    private boolean isFailHeader(ByteBuf headerData) {
        return headerData.getByte(0) == 'F' &&
            headerData.getByte(1) == 'A' &&
            headerData.getByte(2) == 'I' &&
            headerData.getByte(3) == 'L';
    }
}
