package org.fesaid.tools.ddmlib.logcat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.fesaid.tools.ddmlib.IDevice;
import org.fesaid.tools.ddmlib.Log.LogLevel;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the headers output by adb logcat -v long -v epoch. */
public final class LogCatLongEpochMessageParser extends LogCatMessageParser {
    public static final Pattern EPOCH_TIME = Pattern.compile("\\d+\\.\\d\\d\\d");

    private static final Pattern HEADER =
            Pattern.compile(
                    "^\\[ +("
                            + EPOCH_TIME
                            + ") +("
                            + PROCESS_ID
                            + "): *("
                            + THREAD_ID
                            + ") ("
                            + PRIORITY
                            + ")/("
                            + TAG
                            + ") +]$");

    public static final DateTimeFormatter EPOCH_TIME_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendValue(ChronoField.INSTANT_SECONDS)
                    .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
                    .toFormatter(Locale.ROOT);

    @Nullable
    @Override
    public LogCatHeader processLogHeader(@NonNull String string, @Nullable IDevice device) {
        Matcher matcher = HEADER.matcher(string);

        if (!matcher.matches()) {
            return null;
        }

        Instant timestamp = EPOCH_TIME_FORMATTER.parse(matcher.group(1), Instant::from);
        int processId = parseProcessId(matcher.group(2));
        int threadId = parseThreadId(matcher.group(3));
        LogLevel priority = parsePriority(matcher.group(4));
        String tag = matcher.group(5);

        mPrevHeader =
                new LogCatHeader(
                        priority,
                        processId,
                        threadId,
                        getPackageName(device, processId),
                        tag,
                        timestamp);

        return mPrevHeader;
    }
}
