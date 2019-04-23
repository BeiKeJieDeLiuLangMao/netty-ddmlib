package org.fesaid.tools.ddmlib;

/**
 * Thrown if installation or uninstallation of application fails.
 */
public class InstallException extends CanceledException {
    private static final long serialVersionUID = 1L;

    private String errorCode;

    public InstallException(Throwable cause) {
        super(cause.getMessage(), cause);
    }

    public InstallException(String message) {
        super(message);
    }

    public InstallException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public InstallException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns true if the installation was canceled by user input. This can typically only
     * happen in the sync phase.
     */
    @Override
    public boolean wasCanceled() {
        Throwable cause = getCause();
        return cause instanceof SyncException && ((SyncException)cause).wasCanceled();
    }
}
