package org.fesaid.tools.ddmlib.logcat;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.GuardedBy;
import java.util.concurrent.TimeUnit;
import org.fesaid.tools.ddmlib.AdbCommandRejectedException;
import org.fesaid.tools.ddmlib.IDevice;
import org.fesaid.tools.ddmlib.IShellOutputReceiver;
import org.fesaid.tools.ddmlib.Log.LogLevel;
import org.fesaid.tools.ddmlib.MultiLineReceiver;
import org.fesaid.tools.ddmlib.ShellCommandUnresponsiveException;
import org.fesaid.tools.ddmlib.TimeoutException;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author AOSP
 */
@SuppressWarnings("unused")
public class LogCatReceiverTask implements Runnable {
    private static final String LOGCAT_COMMAND = "logcat -v long";
    private static final int DEVICE_POLL_INTERVAL_MSEC = 1000;

    private static final LogCatMessage S_DEVICE_DISCONNECTED_MSG =
            new LogCatMessage(LogLevel.ERROR, "Device disconnected: 1");
    private static final LogCatMessage S_CONNECTION_TIMEOUT_MSG =
            new LogCatMessage(LogLevel.ERROR, "LogCat Connection timed out");
    private static final LogCatMessage S_CONNECTION_ERROR_MSG =
            new LogCatMessage(LogLevel.ERROR, "LogCat Connection error");

    private final IDevice mDevice;
    private final LogCatOutputReceiver mReceiver;
    private final LogCatMessageParser mParser;
    private final AtomicBoolean mCancelled;

    @GuardedBy("this")
    private final Set<LogCatListener> mListeners = new HashSet<>();

    public LogCatReceiverTask(@NonNull IDevice device) {
        mDevice = device;

        mReceiver = new LogCatOutputReceiver();
        mParser = new LogCatMessageParser();
        mCancelled = new AtomicBoolean();
    }

    @Override
    public void run() {
        // wait while device comes online
        while (!mDevice.isOnline()) {
            try {
                Thread.sleep(DEVICE_POLL_INTERVAL_MSEC);
            } catch (InterruptedException e) {
                return;
            }
        }

        try {
            mDevice.executeShellCommand(LOGCAT_COMMAND, mReceiver, Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (TimeoutException e) {
            notifyListeners(Collections.singletonList(S_CONNECTION_TIMEOUT_MSG));
        } catch (AdbCommandRejectedException ignored) {
            // will not be thrown as long as the shell supports logcat
        } catch (ShellCommandUnresponsiveException ignored) {
            // this will not be thrown since the last argument is 0
        } catch (IOException e) {
            notifyListeners(Collections.singletonList(S_CONNECTION_ERROR_MSG));
        }

        notifyListeners(Collections.singletonList(S_DEVICE_DISCONNECTED_MSG));
    }

    public void stop() {
        mCancelled.set(true);
    }

    @SuppressWarnings("WeakerAccess")
    private class LogCatOutputReceiver extends MultiLineReceiver {
        public LogCatOutputReceiver() {
            setTrimLine(false);
        }

        /** Implements {@link IShellOutputReceiver#isCancelled() }. */
        @Override
        public boolean isCancelled() {
            return mCancelled.get();
        }

        @Override
        public void processNewLines(@NonNull String[] lines) {
            if (!mCancelled.get()) {
                processLogLines(lines);
            }
        }

        private void processLogLines(String[] lines) {
            List<LogCatMessage> newMessages = mParser.processLogLines(lines, mDevice);
            if (!newMessages.isEmpty()) {
                notifyListeners(newMessages);
            }
        }
    }

    public synchronized void addLogCatListener(LogCatListener l) {
        mListeners.add(l);
    }

    public synchronized void removeLogCatListener(LogCatListener l) {
        mListeners.remove(l);
    }

    private synchronized void notifyListeners(List<LogCatMessage> messages) {
        for (LogCatListener l: mListeners) {
            l.log(messages);
        }
    }
}
