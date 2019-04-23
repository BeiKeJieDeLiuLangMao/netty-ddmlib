package org.fesaid.tools.ddmlib.log;

import java.io.Serializable;

/**
 * Exception thrown when accessing an {@link EventContainer} value with the wrong type.
 */
public final class InvalidTypeException extends Exception {

    /**
     * Needed by {@link Serializable}.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the default detail message.
     * @see Exception
     */
    public InvalidTypeException() {
        super("Invalid Type");
    }

    /**
     * Constructs a new exception with the specified detail message.
     * @param message the detail message. The detail message is saved for later retrieval
     * by the {@link Throwable#getMessage()} method.
     * @see Exception
     */
    public InvalidTypeException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified cause and a detail message of
     * <code>(cause==null ? null : cause.toString())</code> (which typically contains
     * the class and detail message of cause).
     * @param cause the cause (which is saved for later retrieval by the
     * {@link Throwable#getCause()} method). (A <code>null</code> value is permitted,
     * and indicates that the cause is nonexistent or unknown.)
     * @see Exception
     */
    public InvalidTypeException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     * @param message the detail message. The detail message is saved for later retrieval
     * by the {@link Throwable#getMessage()} method.
     * @param cause the cause (which is saved for later retrieval by the
     * {@link Throwable#getCause()} method). (A <code>null</code> value is permitted,
     * and indicates that the cause is nonexistent or unknown.)
     * @see Exception
     */
    public InvalidTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
