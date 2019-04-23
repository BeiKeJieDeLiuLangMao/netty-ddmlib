package org.fesaid.tools.ddmlib;

/**
 * Centralized point to provide a {@link IDebugPortProvider} to ddmlib.
 *
 * <p>When {@link Client} objects are created, they start listening for debuggers on a specific
 * port. The default behavior is to start with {@link DdmPreferences#getDebugPortBase()} and
 * increment this value for each new <code>Client</code>.
 *
 * <p>This {@link DebugPortManager} allows applications using ddmlib to provide a custom
 * port provider on a per-<code>Client</code> basis, depending on the device/emulator they are
 * running on, and/or their names.
 */
public class DebugPortManager {

    /**
     * Classes which implement this interface provide a method that provides a non random
     * debugger port for a newly created {@link Client}.
     */
    public interface IDebugPortProvider {

        int NO_STATIC_PORT = -1;

        /**
         * Returns a non-random debugger port for the specified application running on the
         * specified {@link Device}.
         * @param device The device the application is running on.
         * @param appName The application name, as defined in the <code>AndroidManifest.xml</code>
         * <var>package</var> attribute of the <var>manifest</var> node.
         * @return The non-random debugger port or {@link #NO_STATIC_PORT} if the {@link Client}
         * should use the automatic debugger port provider.
         */
        int getPort(IDevice device, String appName);
    }

    private static IDebugPortProvider sProvider = null;

    /**
     * Sets the {@link IDebugPortProvider} that will be used when a new {@link Client} requests
     * a debugger port.
     * @param provider the <code>IDebugPortProvider</code> to use.
     */
    public static void setProvider(IDebugPortProvider provider) {
        sProvider = provider;
    }

    /**
     * Returns the
     * @return
     */
    static IDebugPortProvider getProvider() {
        return sProvider;
    }
}
