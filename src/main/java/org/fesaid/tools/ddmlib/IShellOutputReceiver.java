package org.fesaid.tools.ddmlib;

/**
 * Classes which implement this interface provide methods that deal with out from a remote shell
 * command on a device/emulator.
 */
public interface IShellOutputReceiver {
    /**
     * Called every time some new data is available.
     * @param data The new data.
     * @param offset The offset at which the new data starts.
     * @param length The length of the new data.
     */
    void addOutput(byte[] data, int offset, int length);

    /**
     * Called at the end of the process execution (unless the process was
     * canceled). This allows the receiver to terminate and flush whatever
     * data was not yet processed.
     */
    void flush();

    /**
     * Cancel method to stop the execution of the remote shell command.
     * @return true to cancel the execution of the command.
     */
    boolean isCancelled();
}
