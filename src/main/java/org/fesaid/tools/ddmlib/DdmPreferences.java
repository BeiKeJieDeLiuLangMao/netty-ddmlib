package org.fesaid.tools.ddmlib;

import java.util.function.Function;

/**
 * Preferences for the ddm library.
 * <p>This class does not handle storing the preferences. It is merely a central point for
 * applications using the ddmlib to override the default values.
 * <p>Various components of the ddmlib query this class to get their values.
 * <p>Calls to some <code>set##()</code> methods will update the components using the values
 * right away, while other methods will have no effect once {@link AndroidDebugBridge#init}
 * has been called.
 * <p>Check the documentation of each method.
 */
public final class DdmPreferences {

    /** Default value for thread update flag upon client connection. */
    private static final boolean DEFAULT_INITIAL_THREAD_UPDATE = false;
    /** Default value for heap update flag upon client connection. */
    private static final boolean DEFAULT_INITIAL_HEAP_UPDATE = false;
    /** Default value for the selected client debug port */
    private static final int DEFAULT_SELECTED_DEBUG_PORT = 8700;
    /** Default value for the debug port base */
    private static final int DEFAULT_DEBUG_PORT_BASE = 8600;
    /** Default value for the logcat {@link Log.LogLevel} */
    private static final Log.LogLevel DEFAULT_LOG_LEVEL = Log.LogLevel.ERROR;
    /** Default timeout values for adb connection (milliseconds) */
    private static final int DEFAULT_TIMEOUT = 5000;
    /** Default profiler buffer size (megabytes) */
    private static final int DEFAULT_PROFILER_BUFFER_SIZE_MB = 8;
    /** Default values for the use of the ADBHOST environment variable. */
    private static final boolean DEFAULT_USE_ADBHOST = false;
    private static final String DEFAULT_ADBHOST_VALUE = "127.0.0.1";

    private static boolean sThreadUpdate = DEFAULT_INITIAL_THREAD_UPDATE;
    private static boolean sInitialHeapUpdate = DEFAULT_INITIAL_HEAP_UPDATE;

    private static int sSelectedDebugPort = DEFAULT_SELECTED_DEBUG_PORT;
    private static int sDebugPortBase = DEFAULT_DEBUG_PORT_BASE;
    private static Log.LogLevel sLogLevel = DEFAULT_LOG_LEVEL;
    private static int sTimeOut = DEFAULT_TIMEOUT;
    private static int sProfilerBufferSizeMb = DEFAULT_PROFILER_BUFFER_SIZE_MB;

    private static boolean sUseAdbHost = DEFAULT_USE_ADBHOST;
    private static String sAdbHostValue = DEFAULT_ADBHOST_VALUE;

    private static boolean openAdbProxy = false;
    private static Integer adbProxyPort;
    private static Function<String, Boolean> openAdbProxyChecker;

    /**
     * Returns the initial {@link Client} flag for thread updates.
     * @see #setInitialThreadUpdate(boolean)
     */
    public static boolean getInitialThreadUpdate() {
        return sThreadUpdate;
    }

    /**
     * Sets the initial {@link Client} flag for thread updates.
     * <p>This change takes effect right away, for newly created {@link Client} objects.
     */
    public static void setInitialThreadUpdate(boolean state) {
        sThreadUpdate = state;
    }

    /**
     * Returns the initial {@link Client} flag for heap updates.
     * @see #setInitialHeapUpdate(boolean)
     */
    public static boolean getInitialHeapUpdate() {
        return sInitialHeapUpdate;
    }

    /**
     * Sets the initial {@link Client} flag for heap updates.
     * <p>If <code>true</code>, the {@link ClientData} will automatically be updated with
     * the VM heap information whenever a GC happens.
     * <p>This change takes effect right away, for newly created {@link Client} objects.
     */
    public static void setInitialHeapUpdate(boolean state) {
        sInitialHeapUpdate = state;
    }

    /**
     * Returns the debug port used by the selected {@link Client}.
     */
    public static int getSelectedDebugPort() {
        return sSelectedDebugPort;
    }

    /**
     * Sets the debug port used by the selected {@link Client}.
     * <p>This change takes effect right away.
     * @param port the new port to use.
     */
    public static void setSelectedDebugPort(int port) {
        sSelectedDebugPort = port;

        MonitorThread monitorThread = MonitorThread.getInstance();
        if (monitorThread != null) {
            monitorThread.setDebugSelectedPort(port);
        }
    }

    /**
     * Returns the debug port used by the first {@link Client}. Following clients, will use the
     * next port.
     */
    public static int getDebugPortBase() {
        return sDebugPortBase;
    }

    /**
     * Sets the debug port used by the first {@link Client}.
     * <p>Once a port is used, the next Client will use port + 1. Quitting applications will
     * release their debug port, and new clients will be able to reuse them.
     * <p>This must be called before {@link AndroidDebugBridge#init}.
     */
    public static void setDebugPortBase(int port) {
        sDebugPortBase = port;
    }

    /**
     * Returns the minimum {@link Log.LogLevel} being displayed.
     */
    public static Log.LogLevel getLogLevel() {
        return sLogLevel;
    }

    /**
     * Sets the minimum {@link Log.LogLevel} to display.
     * <p>This change takes effect right away.
     */
    public static void setLogLevel(String value) {
        sLogLevel = Log.LogLevel.getByString(value);

        Log.setLevel(sLogLevel);
    }

    /**
     * Returns the timeout to be used in adb connections (milliseconds).
     */
    public static int getTimeOut() {
        return sTimeOut;
    }

    /**
     * Sets the timeout value for adb connection.
     * <p>This change takes effect for newly created connections only.
     * @param timeOut the timeout value (milliseconds).
     */
    public static void setTimeOut(int timeOut) {
        sTimeOut = timeOut;
    }

    /**
     * Returns the profiler buffer size (megabytes).
     */
    public static int getProfilerBufferSizeMb() {
        return sProfilerBufferSizeMb;
    }

    /**
     * Sets the profiler buffer size value.
     * @param bufferSizeMb the buffer size (megabytes).
     */
    public static void setProfilerBufferSizeMb(int bufferSizeMb) {
        sProfilerBufferSizeMb = bufferSizeMb;
    }

    /**
     * Returns a boolean indicating that the user uses or not the variable ADBHOST.
     */
    public static boolean getUseAdbHost() {
        return sUseAdbHost;
    }

    /**
     * Sets the value of the boolean indicating that the user uses or not the variable ADBHOST.
     * @param useAdbHost true if the user uses ADBHOST
     */
    public static void setUseAdbHost(boolean useAdbHost) {
        sUseAdbHost = useAdbHost;
    }

    /**
     * Returns the value of the ADBHOST variable set by the user.
     */
    public static String getAdbHostValue() {
        return sAdbHostValue;
    }

    /**
     * Sets the value of the ADBHOST variable.
     * @param adbHostValue
     */
    public static void setAdbHostValue(String adbHostValue) {
        sAdbHostValue = adbHostValue;
    }

    public static void setOpenAdbProxy(boolean open) {
        openAdbProxy = open;
    }

    public static boolean isOpenAdbProxy() {
        return openAdbProxy;
    }

    public static int getAdbProxyPort() {
        return adbProxyPort;
    }

    public static void setAdbProxyPort(int port) {
        adbProxyPort = port;
    }

    public static boolean shouldOpenAdbProxy(String serialNumber) {
        return openAdbProxyChecker != null && openAdbProxyChecker.apply(serialNumber);
    }

    public static void setOpenAdbProxyChecker(Function<String, Boolean> checker) {
        openAdbProxyChecker = checker;
    }

    /**
     * Non accessible constructor.
     */
    private DdmPreferences() {
        // pass, only static methods in the class.
    }
}
