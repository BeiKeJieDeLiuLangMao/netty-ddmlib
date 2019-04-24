package org.fesaid.tools.ddmlib.logcat;

import com.android.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.fesaid.tools.ddmlib.Log.LogLevel;

/**
 * Model a single log message output from {@code logcat -v long}.
 *
 * Every message is furthermore associated with a {@link LogCatHeader} which contains additionally
 * meta information about the message.
 */
public final class LogCatMessage {

    @NonNull
    private final LogCatHeader mHeader;

    @NonNull
    private final List<String> mMessage = new ArrayList<>();

    /**
     * @deprecated Create a {@link LogCatHeader} separately and call {@link
     * #LogCatMessage(LogCatHeader, String)} instead. This approach shares the same header data
     * across multiple messages.
     */
    @Deprecated
    public LogCatMessage(@NonNull LogLevel logLevel, int pid, int tid,
            @NonNull String appName, @NonNull String tag,
            @NonNull LogCatTimestamp timestamp, @NonNull String msg) {
        this(new LogCatHeader(logLevel, pid, tid, appName, tag, timestamp), msg);
    }

    /**
     * Construct an immutable log message object.
     */
    public LogCatMessage(@NonNull LogCatHeader header, @NonNull String msg) {
        mHeader = header;
        mMessage.add(msg);
    }

    /**
     * Helper constructor to generate a dummy message, useful if we want to add message from code
     * that matches the logcat format.
     */
    public LogCatMessage(@NonNull LogLevel logLevel, @NonNull String message) {
        this(logLevel, -1, -1, "", "", LogCatTimestamp.ZERO, message);
    }


    @NonNull
    public LogCatHeader getHeader() {
        return mHeader;
    }

    @NonNull
    public List<String> getMessage() {
        return mMessage;
    }

    @NonNull
    public LogLevel getLogLevel() {
        return mHeader.getLogLevel();
    }

    public int getPid() {
        return mHeader.getPid();
    }

    public int getTid() {
        return mHeader.getTid();
    }

    @NonNull
    public String getAppName() {
        return mHeader.getAppName();
    }

    @NonNull
    public String getTag() {
        return mHeader.getTag();
    }

    @NonNull
    public LogCatTimestamp getTimestamp() {
        return mHeader.getTimestamp();
    }

    @Override
    public String toString() {
        return mHeader.toString() + ": " + mMessage;
    }
}
