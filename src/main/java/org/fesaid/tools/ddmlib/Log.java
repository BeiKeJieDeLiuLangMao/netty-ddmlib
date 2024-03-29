package org.fesaid.tools.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Sets;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * Log class that mirrors the API in main Android sources.
 * <p>Default behavior outputs the log to {@link System#out}. Use
 * {@link #addLogger(ILogOutput)} to redirect the log somewhere else.
 */
public final class Log {

    /**
     * Log Level enum.
     */
    public enum LogLevel {
        VERBOSE(2, "verbose", 'V'), //$NON-NLS-1$
        DEBUG(3, "debug", 'D'), //$NON-NLS-1$
        INFO(4, "info", 'I'), //$NON-NLS-1$
        WARN(5, "warn", 'W'), //$NON-NLS-1$
        ERROR(6, "error", 'E'), //$NON-NLS-1$
        ASSERT(7, "assert", 'A'); //$NON-NLS-1$

        private int mPriorityLevel;
        private String mStringValue;
        private char mPriorityLetter;

        LogLevel(int intPriority, String stringValue, char priorityChar) {
            mPriorityLevel = intPriority;
            mStringValue = stringValue;
            mPriorityLetter = priorityChar;
        }

        public static LogLevel getByString(String value) {
            for (LogLevel mode : values()) {
                if (mode.mStringValue.equals(value)) {
                    return mode;
                }
            }

            return null;
        }
        
        /**
         * Returns the {@link LogLevel} enum matching the specified letter.
         * @param letter the letter matching a <code>LogLevel</code> enum
         * @return a <code>LogLevel</code> object or <code>null</code> if no match were found.
         */
        public static LogLevel getByLetter(char letter) {
            for (LogLevel mode : values()) {
                if (mode.mPriorityLetter == letter) {
                    return mode;
                }
            }

            return null;
        }

        /**
         * Returns the {@link LogLevel} enum matching the specified letter.
         * <p>
         * The letter is passed as a {@link String} argument, but only the first character
         * is used. 
         * @param letter the letter matching a <code>LogLevel</code> enum
         * @return a <code>LogLevel</code> object or <code>null</code> if no match were found.
         */
        public static LogLevel getByLetterString(String letter) {
            if (!letter.isEmpty()) {
                return getByLetter(letter.charAt(0));
            }

            return null;
        }

        /**
         * Returns the letter identifying the priority of the {@link LogLevel}.
         */
        public char getPriorityLetter() {
            return mPriorityLetter;
        }

        /**
         * Returns the numerical value of the priority.
         */
        public int getPriority() {
            return mPriorityLevel;
        }

        /**
         * Returns a non translated string representing the LogLevel.
         */
        public String getStringValue() {
            return mStringValue;
        }
    }
    
    /**
     * Classes which implement this interface provides methods that deal with outputting log
     * messages.
     */
    public interface ILogOutput {
        /**
         * Sent when a log message needs to be printed.
         * @param logLevel The {@link LogLevel} enum representing the priority of the message.
         * @param tag The tag associated with the message.
         * @param message The message to display.
         */
        void printLog(LogLevel logLevel, String tag, String message);

        /**
         * Sent when a log message needs to be printed, and, if possible, displayed to the user
         * in a dialog box.
         * @param logLevel The {@link LogLevel} enum representing the priority of the message.
         * @param tag The tag associated with the message.
         * @param message The message to display.
         */
        void printAndPromptLog(LogLevel logLevel, String tag, String message);
    }

    private static LogLevel sLevel = DdmPreferences.getLogLevel();

    @Nullable
    private static ILogOutput sLogOutput;

    @NonNull
    private static final Set<ILogOutput> sOutputLoggers = Sets.newCopyOnWriteArraySet();

    private static final char[] mSpaceLine = new char[72];
    private static final char[] mHexDigit = new char[]
        { '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
    static {
        /* prep for hex dump */
        int i = mSpaceLine.length-1;
        while (i >= 0)
            mSpaceLine[i--] = ' ';
        mSpaceLine[0] = mSpaceLine[1] = mSpaceLine[2] = mSpaceLine[3] = '0';
        mSpaceLine[4] = '-';
    }

    static final class Config {
        static final boolean LOGV = true;
        static final boolean LOGD = true;
    }

    private Log() {}

    /**
     * Outputs a {@link LogLevel#VERBOSE} level message.
     * @param tag The tag associated with the message.
     * @param message The message to output.
     */
    public static void v(String tag, String message) {
        println(LogLevel.VERBOSE, tag, message);
    }

    /**
     * Outputs a {@link LogLevel#DEBUG} level message.
     * @param tag The tag associated with the message.
     * @param message The message to output.
     */
    public static void d(String tag, String message) {
        println(LogLevel.DEBUG, tag, message);
    }

    /**
     * Outputs a {@link LogLevel#INFO} level message.
     * @param tag The tag associated with the message.
     * @param message The message to output.
     */
    public static void i(String tag, String message) {
        println(LogLevel.INFO, tag, message);
    }

    /**
     * Outputs a {@link LogLevel#WARN} level message.
     * @param tag The tag associated with the message.
     * @param message The message to output.
     */
    public static void w(String tag, String message) {
        println(LogLevel.WARN, tag, message);
    }

    /**
     * Outputs a {@link LogLevel#ERROR} level message.
     * @param tag The tag associated with the message.
     * @param message The message to output.
     */
    public static void e(String tag, String message) {
        println(LogLevel.ERROR, tag, message);
    }

    /**
     * Outputs a log message and attempts to display it in a dialog.
     * @param tag The tag associated with the message.
     * @param message The message to output.
     */
    public static void logAndDisplay(LogLevel logLevel, String tag, String message) {
        if (!sOutputLoggers.isEmpty()) {
            for (ILogOutput logger : sOutputLoggers) {
                logger.printAndPromptLog(logLevel, tag, message);
            }
        }

        if (sLogOutput != null) {
            sLogOutput.printAndPromptLog(logLevel, tag, message);
        } else if (sOutputLoggers.isEmpty()) {
            println(logLevel, tag, message);
        }
    }

    /**
     * Outputs a {@link LogLevel#ERROR} level {@link Throwable} information.
     * @param tag The tag associated with the message.
     * @param throwable The {@link Throwable} to output.
     */
    public static void e(String tag, Throwable throwable) {
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);

            throwable.printStackTrace(pw);
            println(LogLevel.ERROR, tag, throwable.getMessage() + '\n' + sw.toString());
        }
    }

    static void setLevel(LogLevel logLevel) {
        sLevel = logLevel;
    }

    static boolean isAtLeast(@NonNull LogLevel logLevel) {
        return logLevel.getPriority() >= sLevel.getPriority();
    }

    /**
     * @deprecated Use {@link #addLogger(ILogOutput)} instead. <p>
     * Sets the {@link ILogOutput} to use to print the logs. If not set, {@link System#out}
     * will be used.
     * @param logOutput The {@link ILogOutput} to use to print the log.
     */
    @Deprecated
    public static void setLogOutput(ILogOutput logOutput) {
        sLogOutput = logOutput;
    }

    public static void addLogger(@NonNull ILogOutput logOutput) {
        sOutputLoggers.add(logOutput);
    }

    public static void removeLogger(@NonNull ILogOutput logOutput) {
        sOutputLoggers.remove(logOutput);
    }

    /**
     * Show hex dump.
     * <p>
     * Local addition.  Output looks like:
     * 1230- 00 11 22 33 44 55 66 77 88 99 aa bb cc dd ee ff  0123456789abcdef
     * <p>
     * Uses no string concatenation; creates one String object per line.
     */
    static void hexDump(String tag, LogLevel level, byte[] data, int offset, int length) {

        int kHexOffset = 6;
        int kAscOffset = 55;
        char[] line = new char[mSpaceLine.length];
        int addr, baseAddr, count;
        int i, ch;
        boolean needErase = true;

        //Log.w(tag, "HEX DUMP: off=" + offset + ", length=" + length);

        baseAddr = 0;
        while (length != 0) {
            if (length > 16) {
                // full line
                count = 16;
            } else {
                // partial line; re-copy blanks to clear end
                count = length;
                needErase = true;
            }

            if (needErase) {
                System.arraycopy(mSpaceLine, 0, line, 0, mSpaceLine.length);
                needErase = false;
            }

            // output the address (currently limited to 4 hex digits)
            addr = baseAddr;
            addr &= 0xffff;
            ch = 3;
            while (addr != 0) {
                line[ch] = mHexDigit[addr & 0x0f];
                ch--;
                addr >>>= 4;
            }

            // output hex digits and ASCII chars
            ch = kHexOffset;
            for (i = 0; i < count; i++) {
                byte val = data[offset + i];

                line[ch++] = mHexDigit[(val >>> 4) & 0x0f];
                line[ch++] = mHexDigit[val & 0x0f];
                ch++;

                if (val >= 0x20 && val < 0x7f)
                    line[kAscOffset + i] = (char) val;
                else
                    line[kAscOffset + i] = '.';
            }

            println(level, tag, new String(line));

            // advance to next chunk of data
            length -= count;
            offset += count;
            baseAddr += count;
        }

    }

    /**
     * Dump the entire contents of a byte array with DEBUG priority.
     */
    static void hexDump(byte[] data) {
        hexDump("ddms", LogLevel.DEBUG, data, 0, data.length);
    }

    private static void println(LogLevel logLevel, String tag, String message) {
        if (!isAtLeast(logLevel)) {
            return;
        }

        if (!sOutputLoggers.isEmpty()) {
            for (ILogOutput logger : sOutputLoggers) {
                logger.printLog(logLevel, tag, message);
            }
        }

        if (sLogOutput != null) {
            sLogOutput.printLog(logLevel, tag, message);
        } else if (sOutputLoggers.isEmpty()) {
            printLog(logLevel, tag, message);
        }
    }

    public static void printLog(LogLevel logLevel, String tag, String message) {
        System.out.print(getLogFormatString(logLevel, tag, message));
    }

    public static String getLogFormatString(LogLevel logLevel, String tag, String message) {
        SimpleDateFormat formatter = new SimpleDateFormat("hh:mm:ss", Locale.getDefault());
        return String.format("%s %c/%s: %s\n", formatter.format(new Date()),
                logLevel.getPriorityLetter(), tag, message);
    }
}


