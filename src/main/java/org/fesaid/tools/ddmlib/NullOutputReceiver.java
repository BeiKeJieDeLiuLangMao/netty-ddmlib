package org.fesaid.tools.ddmlib;

/**
 * Implementation of {@link IShellOutputReceiver} that does nothing.
 * <p>This can be used to execute a remote shell command when the output is not needed.
 */
public final class NullOutputReceiver implements IShellOutputReceiver {

    private static NullOutputReceiver sReceiver = new NullOutputReceiver();

    public static IShellOutputReceiver getReceiver() {
        return sReceiver;
    }

    /* (non-Javadoc)
     * @see com.android.ddmlib.adb.IShellOutputReceiver#addOutput(byte[], int, int)
     */
    @Override
    public void addOutput(byte[] data, int offset, int length) {
    }

    /* (non-Javadoc)
     * @see com.android.ddmlib.adb.IShellOutputReceiver#flush()
     */
    @Override
    public void flush() {
    }

    /* (non-Javadoc)
     * @see com.android.ddmlib.adb.IShellOutputReceiver#isCancelled()
     */
    @Override
    public boolean isCancelled() {
        return false;
    }

}
