package org.fesaid.tools.ddmlib;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Base implementation of {@link IShellOutputReceiver}, that takes the raw data coming from the
 * socket, and convert it into {@link String} objects.
 *
 * <p>Additionally, it splits the string by lines.
 *
 * <p>Classes extending it must implement {@link #processNewLines(String[])} which receives new
 * parsed lines as they become available.
 * @author AOSP
 */
public abstract class MultiLineReceiver implements IShellOutputReceiver {

    private boolean mTrimLines = true;

    /** unfinished message line, stored for next packet */
    private String mUnfinishedLine = null;

    private final Collection<String> mArray = new ArrayList<>();

    /**
     * Set the trim lines flag.
     *
     * @param trim whether the lines are trimmed, or not.
     */
    public void setTrimLine(boolean trim) {
        mTrimLines = trim;
    }

    @Override
    public final void addOutput(byte[] data, int offset, int length) {
        if (!isCancelled()) {
            String s = new String(data, offset, length, Charsets.UTF_8);

            // ok we've got a string
            // if we had an unfinished line we add it.
            if (mUnfinishedLine != null) {
                s = mUnfinishedLine + s;
                mUnfinishedLine = null;
            }

            // now we split the lines
            mArray.clear();
            int start = 0;
            do {
                int index = s.indexOf('\n', start);

                // if \n was not found, this is an unfinished line and we store it to be processed for the next packet

                if (index == -1) {
                    mUnfinishedLine = s.substring(start);
                    break;
                }

                // we found a \n, in older devices, this is preceded by a \r
                int newlineLength = 1;
                if (index > 0 && s.charAt(index - 1) == '\r') {
                    index--;
                    newlineLength = 2;
                }

                // extract the line
                String line = s.substring(start, index);
                if (mTrimLines) {
                    line = line.trim();
                }
                mArray.add(line);

                // move start to after the \r\n we found
                start = index + newlineLength;
            } while (true);

            if (!mArray.isEmpty()) {
                // at this point we've split all the lines.
                // make the array
                String[] lines = mArray.toArray(new String[0]);

                // send it for final processing
                processNewLines(lines);
            }
        }
    }

    @Override
    public void flush() {
        if (mUnfinishedLine != null) {
            processNewLines(new String[] {mUnfinishedLine});
        }

        done();
    }

    /**
     * Terminates the process. This is called after the last lines have been through {@link
     * #processNewLines(String[])}.
     */
    public void done() {
        // do nothing.
    }

    /**
     * Called when new lines are being received by the remote process.
     *
     * <p>It is guaranteed that the lines are complete when they are given to this method.
     *
     * @param lines The array containing the new lines.
     */
    public abstract void processNewLines(@NonNull String[] lines);
}
