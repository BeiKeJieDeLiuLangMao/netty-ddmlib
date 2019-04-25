package org.fesaid.tools.ddmlib;


/**
 * Exception thrown when adb refuses a command.
 * @author AOSP
 */
@SuppressWarnings("unused")
public class AdbCommandRejectedException extends Exception {
    private static final long serialVersionUID = 1L;
    private final boolean mIsDeviceOffline;
    private final boolean mErrorDuringDeviceSelection;

    public AdbCommandRejectedException(String message) {
        super(message);
        mIsDeviceOffline = "device offline".equals(message);
        mErrorDuringDeviceSelection = false;
    }

    AdbCommandRejectedException(String message, boolean errorDuringDeviceSelection) {
        super(message);
        mErrorDuringDeviceSelection = errorDuringDeviceSelection;
        mIsDeviceOffline = "device offline".equals(message);
    }

    /**
     * Returns true if the error is due to the device being offline.
     */
    public boolean isDeviceOffline() {
        return mIsDeviceOffline;
    }

    /**
     * Returns whether adb refused to target a given device for the command.
     * <p>If false, adb refused the command itself, if true, it refused to target the given
     * device.
     */
    public boolean wasErrorDuringDeviceSelection() {
        return mErrorDuringDeviceSelection;
    }
}
