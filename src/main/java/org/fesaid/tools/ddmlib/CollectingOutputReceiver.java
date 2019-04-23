package org.fesaid.tools.ddmlib;


import com.google.common.base.Charsets;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link IShellOutputReceiver} which collects the whole shell output into one
 * {@link String}.
 */
public class CollectingOutputReceiver implements IShellOutputReceiver {
    private CountDownLatch mCompletionLatch;
    private StringBuffer mOutputBuffer = new StringBuffer();
    private AtomicBoolean mIsCanceled = new AtomicBoolean(false);

    public CollectingOutputReceiver() {
    }

    public CollectingOutputReceiver(CountDownLatch commandCompleteLatch) {
        mCompletionLatch = commandCompleteLatch;
    }

    public String getOutput() {
        return mOutputBuffer.toString();
    }

    @Override
    public boolean isCancelled() {
        return mIsCanceled.get();
    }

    /**
     * Cancel the output collection
     */
    public void cancel() {
        mIsCanceled.set(true);
    }

    @Override
    public void addOutput(byte[] data, int offset, int length) {
        if (!isCancelled()) {
            String s;
            s = new String(data, offset, length, Charsets.UTF_8);
            mOutputBuffer.append(s);
        }
    }

    @Override
    public void flush() {
        if (mCompletionLatch != null) {
            mCompletionLatch.countDown();
        }
    }
}
