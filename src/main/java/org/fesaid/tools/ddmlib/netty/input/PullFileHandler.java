package org.fesaid.tools.ddmlib.netty.input;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.fesaid.tools.ddmlib.AdbHelper;
import org.fesaid.tools.ddmlib.SyncException;
import org.fesaid.tools.ddmlib.SyncService;
import org.fesaid.tools.ddmlib.TimeoutException;

import static org.fesaid.tools.ddmlib.SyncException.SyncError.FILE_WRITE_ERROR;
import static org.fesaid.tools.ddmlib.SyncException.SyncError.TRANSFER_PROTOCOL_ERROR;
import static org.fesaid.tools.ddmlib.SyncService.HEADER_LENGTH;
import static org.fesaid.tools.ddmlib.SyncService.SYNC_DATA_MAX;
import static org.fesaid.tools.ddmlib.SyncService.State.WAIT_DATA;
import static org.fesaid.tools.ddmlib.SyncService.State.WAIT_ERROR_MESSAGE;
import static org.fesaid.tools.ddmlib.SyncService.State.WAIT_HEADER;
import static org.fesaid.tools.ddmlib.utils.ArrayHelper.swap32bitFromArray;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class PullFileHandler extends ByteToMessageDecoder implements AdbInputHandler {

    private byte[] headerData = new byte[HEADER_LENGTH];
    private CountDownLatch respondBegin = new CountDownLatch(1);
    private CountDownLatch done = new CountDownLatch(1);
    private SyncService.ISyncProgressMonitor monitor;
    private File localFile;
    private FileChannel fileChannel;
    private boolean success;
    private SyncException cause;
    private SyncService.State state = WAIT_HEADER;
    private int dataLength;

    public PullFileHandler(SyncService.ISyncProgressMonitor monitor, File localFile) {
        this.monitor = monitor;
        this.localFile = localFile;
        setCumulator(COMPOSITE_CUMULATOR);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws IOException, SyncException {
        if (monitor.isCanceled()) {
            throw new SyncException(SyncException.SyncError.CANCELED);
        }
        switch (state) {
            case WAIT_HEADER:
                if (in.readableBytes() >= HEADER_LENGTH) {
                    in.readBytes(headerData);
                    handleHeaderRead();
                }
                break;
            case WAIT_DATA:
                if (in.readableBytes() >= dataLength) {
                    handleSaveData(in);
                }
                break;
            case WAIT_ERROR_MESSAGE:
                if (in.readableBytes() >= dataLength) {
                    throw new SyncException(TRANSFER_PROTOCOL_ERROR,
                        in.readBytes(dataLength).toString(AdbHelper.DEFAULT_CHARSET));
                }
                break;
            default:
                throw new RuntimeException("Should never happen.");
        }
    }

    private void handleHeaderRead() throws SyncException, IOException {
        if (respondBegin.getCount() > 0) {
            respondBegin.countDown();
        }
        if (isDataHeader()) {
            state = WAIT_DATA;
            dataLength = swap32bitFromArray(headerData, 4);
            if (dataLength > SYNC_DATA_MAX) {
                // buffer overrun!
                // error and exit
                throw new SyncException(SyncException.SyncError.BUFFER_OVERRUN);
            }
        } else if (isDoneHeader()) {
            makeSureFileCreated();
            finished();
        } else if (isFailHeader()) {
            state = WAIT_ERROR_MESSAGE;
            dataLength = swap32bitFromArray(headerData, 4);
        } else {
            throw new SyncException(TRANSFER_PROTOCOL_ERROR);
        }
    }

    private void handleSaveData(ByteBuf in) throws IOException {
        makeSureFileCreated();
        fileChannel.write(in.nioBuffer(in.readerIndex(), dataLength));
        in.readerIndex(in.readerIndex() + dataLength);
        monitor.advance(dataLength);
        state = WAIT_HEADER;
    }

    private void makeSureFileCreated() throws IOException {
        if (fileChannel == null) {
            if (!localFile.exists()) {
                if (!localFile.getParentFile().exists()) {
                    if (!localFile.getParentFile().mkdir()) {
                        throw new IOException("Create parent directory failed.");
                    }
                }
                if (!localFile.createNewFile()) {
                    throw new IOException("Create file failed.");
                }
            }
            fileChannel = FileChannel.open(localFile.toPath(), StandardOpenOption.WRITE);
        }
    }

    private void finished() {
        success = true;
        if (fileChannel != null) {
            try {
                fileChannel.close();
            } catch (IOException ignored) {
            }
        }
        done.countDown();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (fileChannel != null) {
            try {
                fileChannel.close();
            } catch (IOException ignored) {
            }
        }
        success = false;
        if (cause instanceof IOException) {
            this.cause = new SyncException(FILE_WRITE_ERROR, cause);
        } else if (cause instanceof SyncException) {
            this.cause = (SyncException) cause;
        } else {
            this.cause = new SyncException(TRANSFER_PROTOCOL_ERROR, cause);
        }
        done.countDown();
    }

    public void waitRespondBegin(int out, TimeUnit milliseconds) throws TimeoutException, SyncException {
        try {
            if (!respondBegin.await(out, milliseconds)) {
                throw new TimeoutException("wait result timeout.");
            }
            if (!isDataHeader() && !isDoneHeader()) {
                throw new SyncException(TRANSFER_PROTOCOL_ERROR);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void waitFinish() throws SyncException {
        try {
            done.await();
            if (!success) {
                throw cause;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isDataHeader() {
        return headerData[0] == 'D' &&
            headerData[1] == 'A' &&
            headerData[2] == 'T' &&
            headerData[3] == 'A';
    }

    private boolean isDoneHeader() {
        return headerData[0] == 'D' &&
            headerData[1] == 'O' &&
            headerData[2] == 'N' &&
            headerData[3] == 'E';
    }

    private boolean isFailHeader() {
        return headerData[0] == 'F' &&
            headerData[1] == 'A' &&
            headerData[2] == 'I' &&
            headerData[3] == 'L';
    }
}
