package org.fesaid.tools.ddmlib;

public final class DdmConstants {

    public static final int PLATFORM_UNKNOWN = 0;
    public static final int PLATFORM_LINUX = 1;
    public static final int PLATFORM_WINDOWS = 2;
    public static final int PLATFORM_DARWIN = 3;

    /**
     * Returns current platform, one of {@link #PLATFORM_WINDOWS}, {@link #PLATFORM_DARWIN},
     * {@link #PLATFORM_LINUX} or {@link #PLATFORM_UNKNOWN}.
     */
    public static final int CURRENT_PLATFORM = currentPlatform();

    public static final String EXTENSION = "trace";
    /**
     * Extension for Traceview files.
     */
    public static final String DOT_TRACE = "." + EXTENSION;

    /** hprof-conv executable (with extension for the current OS)  */
    public static final String FN_HPROF_CONVERTER = (CURRENT_PLATFORM == PLATFORM_WINDOWS) ?
            "hprof-conv.exe" : "hprof-conv"; //$NON-NLS-1$ //$NON-NLS-2$

    /** traceview executable (with extension for the current OS)  */
    public static final String FN_TRACEVIEW = (CURRENT_PLATFORM == PLATFORM_WINDOWS) ?
            "traceview.bat" : "traceview"; //$NON-NLS-1$ //$NON-NLS-2$

    /**
     * Returns current platform
     *
     * @return one of {@link #PLATFORM_WINDOWS}, {@link #PLATFORM_DARWIN},
     * {@link #PLATFORM_LINUX} or {@link #PLATFORM_UNKNOWN}.
     */
    public static int currentPlatform() {
        String os = System.getProperty("os.name");          //$NON-NLS-1$
        if (os.startsWith("Mac OS")) {                      //$NON-NLS-1$
            return PLATFORM_DARWIN;
        } else if (os.startsWith("Windows")) {              //$NON-NLS-1$
            return PLATFORM_WINDOWS;
        } else if (os.startsWith("Linux")) {                //$NON-NLS-1$
            return PLATFORM_LINUX;
        }

        return PLATFORM_UNKNOWN;
    }

}
