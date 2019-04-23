package org.fesaid.tools.ddmlib;

/**
 * Abstract exception for exception that can be thrown when a user input cancels the action.
 * <p>
 * {@link #wasCanceled()} returns whether the action was canceled because of user input.
 *
 */
public abstract class CanceledException extends Exception {
    private static final long serialVersionUID = 1L;

    CanceledException(String message) {
        super(message);
    }

    CanceledException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns true if the action was canceled by user input.
     */
    public abstract boolean wasCanceled();
}
