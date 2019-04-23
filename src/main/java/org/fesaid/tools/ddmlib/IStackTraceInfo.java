package org.fesaid.tools.ddmlib;

/**
 * Classes which implement this interface provide a method that returns a stack trace.
 */
public interface IStackTraceInfo {

    /**
     * Returns the stack trace. This can be <code>null</code>.
     */
    StackTraceElement[] getStackTrace();

}
