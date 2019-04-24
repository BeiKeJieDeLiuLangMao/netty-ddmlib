package org.fesaid.tools.ddmlib;


/**
 * Exception thrown when a shell command executed on a device takes too long to send its output.
 * <p>The command may not actually be unresponsive, it just has spent too much time not outputting
 * any thing to the console.
 * @author AOSP
 */
public class ShellCommandUnresponsiveException extends Exception {
    private static final long serialVersionUID = 1L;
}
