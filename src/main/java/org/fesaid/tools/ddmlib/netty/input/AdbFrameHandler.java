package org.fesaid.tools.ddmlib.netty.input;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.RawImage;
import org.fesaid.tools.ddmlib.TimeoutException;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@Slf4j
public class AdbFrameHandler extends ChannelInboundHandlerAdapter implements AdbInputHandler {

    private static final int VERSION_FIELD_SIZE = 4;
    private byte[] versionBytes = new byte[4];
    private byte[] headerBytes;
    private RawImage imageParams = new RawImage();
    private Integer version;
    private boolean readHeader = false;
    private int readLength = 0;
    private CountDownLatch finish = new CountDownLatch(1);
    private boolean success;
    private Throwable cause;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (finish.getCount() > 0) {
            if (Objects.isNull(version)) {
                if (((ByteBuf) msg).readableBytes() >= VERSION_FIELD_SIZE) {
                    ((ByteBuf) msg).readBytes(versionBytes, 0, 4);
                    ByteBuffer buf = ByteBuffer.wrap(versionBytes);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    version = buf.getInt();
                    headerBytes = new byte[RawImage.getHeaderSize(version) * 4];
                    log.debug("Image version, " + version);
                    if (((ByteBuf) msg).readableBytes() > 0) {
                        channelRead(ctx, msg);
                    }
                }
            } else if (!readHeader) {
                if (((ByteBuf) msg).readableBytes() >= headerBytes.length) {
                    readHeader = true;
                    ((ByteBuf) msg).readBytes(headerBytes, 0, headerBytes.length);
                    ByteBuffer buf = ByteBuffer.wrap(headerBytes);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    if (!imageParams.readHeader(version, buf)) {
                        imageParams = null;
                        finish.countDown();
                    }
                    log.debug("Image params: bpp=" + imageParams.bpp + ", size=" + imageParams.size + ", width=" +
                        imageParams.width + ", height=" + imageParams.height);
                    ctx.writeAndFlush(ctx.alloc().buffer(1).setByte(0, 0));
                    imageParams.data = new byte[imageParams.size];
                    if (((ByteBuf) msg).readableBytes() > 0) {
                        channelRead(ctx, msg);
                    }
                }
            } else {
                int length = Math.min(((ByteBuf)msg).readableBytes(), imageParams.size - readLength);
                ((ByteBuf) msg).readBytes(imageParams.data, readLength, length);
                readLength += length;
                if (readLength >= imageParams.size) {
                    success = true;
                    finish.countDown();
                }
            }
        } else {
            unhandledData(ctx, msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (finish.getCount() > 0) {
            success = false;
            this.cause = new Exception("Connection inactive");
            finish.countDown();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        success = false;
        this.cause = cause;
        finish.countDown();
    }

    public RawImage waitFrameData(long timeout, TimeUnit unit) throws TimeoutException, IOException {
        try {
            if (timeout > 0) {
                if (!finish.await(timeout, unit)) {
                    throw new TimeoutException("wait frame timeout");
                }
            } else {
                finish.await();
            }
            if (!success) {
                throw new IOException(cause);
            }
            return imageParams;
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
    }
}