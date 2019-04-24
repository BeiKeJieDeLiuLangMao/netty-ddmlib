package org.fesaid.tools.ddmlib;


/**
 * Exception thrown when a connection to Adb failed with a timeout.
 *
 * @author AOSP
 */
public class TimeoutException extends Exception {
    private static final long serialVersionUID = 1L;

    public TimeoutException() {
    }

    public TimeoutException(String s) {
        super(s);
    }

    public TimeoutException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public TimeoutException(Throwable throwable) {
        super(throwable);
    }
}
