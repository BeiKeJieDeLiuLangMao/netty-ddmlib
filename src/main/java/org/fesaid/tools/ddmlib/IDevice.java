package org.fesaid.tools.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import io.netty.buffer.ByteBuf;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.fesaid.tools.ddmlib.log.LogReceiver;

/**
 * A Device. It can be a physical device or an emulator.
 *
 * @author AOSP
 */
@SuppressWarnings("unused")
public interface IDevice extends IShellEnabledDevice {

    String PROP_BUILD_VERSION = "ro.build.version.release";
    String PROP_BUILD_API_LEVEL = "ro.build.version.sdk";
    String PROP_BUILD_CODENAME = "ro.build.version.codename";
    String PROP_BUILD_TAGS = "ro.build.tags";
    String PROP_BUILD_TYPE = "ro.build.type";
    String PROP_DEVICE_MODEL = "ro.product.model";
    String PROP_DEVICE_MANUFACTURER = "ro.product.manufacturer";
    String PROP_DEVICE_CPU_ABI_LIST = "ro.product.cpu.abilist";
    String PROP_DEVICE_CPU_ABI = "ro.product.cpu.abi";
    String PROP_DEVICE_CPU_ABI2 = "ro.product.cpu.abi2";
    String PROP_BUILD_CHARACTERISTICS = "ro.build.characteristics";
    String PROP_DEVICE_DENSITY = "ro.sf.lcd_density";
    String PROP_DEVICE_EMULATOR_DENSITY = "qemu.sf.lcd_density";
    String PROP_DEVICE_LANGUAGE = "persist.sys.language";
    String PROP_DEVICE_REGION = "persist.sys.country";

    String PROP_DEBUGGABLE = "ro.debuggable";

    /** Serial number of the first connected emulator. */
    String FIRST_EMULATOR_SN = "emulator-5554";
    /** Device change bit mask: {@link DeviceState} change. */
    int CHANGE_STATE = 0x0001;
    /** Device change bit mask: {@link Client} list change. */
    int CHANGE_CLIENT_LIST = 0x0002;
    /** Device change bit mask: build info change. */
    int CHANGE_BUILD_INFO = 0x0004;

    /** Device level software features. */
    enum Feature {
        /**
         * screen recorder available?
         */
        SCREEN_RECORD,
        /**
         * procstats service (dumpsys procstats) available
         */
        PROCSTATS
    }

    enum HardwareFeature {
        /**
         * Device level hardware features.
         */
        WATCH("watch"),
        EMBEDDED("embedded"),
        TV("tv");

        private final String mCharacteristic;

        HardwareFeature(String characteristic) {
            mCharacteristic = characteristic;
        }

        public String getCharacteristic() {
            return mCharacteristic;
        }
    }

    String MNT_EXTERNAL_STORAGE = "EXTERNAL_STORAGE";
    String MNT_ROOT = "ANDROID_ROOT";
    String MNT_DATA = "ANDROID_DATA";

    enum DeviceState {
        /**
         * The state of a device.
         */
        BOOTLOADER("bootloader"),
        OFFLINE("offline"),
        ONLINE("device"),
        RECOVERY("recovery"),
        /** Device is in "sideload" state either through `adb sideload` or recovery menu */
        SIDELOAD("sideload"),
        UNAUTHORIZED("unauthorized"),
        DISCONNECTED("disconnected"),
        ;

        private String mState;

        DeviceState(String state) {
            mState = state;
        }

        /**
         * Returns a {@link DeviceState} from the string returned by <code>adb devices</code>.
         *
         * @param state the device state.
         * @return a {@link DeviceState} object or <code>null</code> if the state is unknown.
         */
        @Nullable
        public static DeviceState getState(String state) {
            for (DeviceState deviceState : values()) {
                if (deviceState.mState.equals(state)) {
                    return deviceState;
                }
            }
            return null;
        }

        public String getState() {
            return mState;
        }
    }

    enum DeviceUnixSocketNamespace {
        /**
         * Namespace of a Unix Domain Socket created on the device.
         */
        ABSTRACT("localabstract"),
        FILESYSTEM("localfilesystem"),
        RESERVED("localreserved");

        private String mType;

        DeviceUnixSocketNamespace(String type) {
            mType = type;
        }

        String getType() {
            return mType;
        }
    }

    /**
     * Returns the serial number of the device.
     *
     * @return serial number
     */
    @NonNull
    String getSerialNumber();

    /**
     * Returns the name of the AVD the emulator is running.
     * <p>This is only valid if {@link #isEmulator()} returns true.
     * <p>If the emulator is not running any AVD (for instance it's running from an Android source
     * tree build), this method will return "<code>&lt;build&gt;</code>".
     *
     * @return the name of the AVD or <code>null</code> if there isn't any.
     */
    @Nullable
    String getAvdName();

    /**
     * Returns the state of the device.
     *
     * @return state
     */
    DeviceState getState();

    /**
     * Convenience method that attempts to retrieve a property via {@link #getSystemProperty(String)} with a very short
     * wait time, and swallows exceptions.
     *
     * <p><em>Note: Prefer using {@link #getSystemProperty(String)} if you want control over the
     * timeout.</em>
     *
     * @param name the name of the value to return.
     * @return the value or <code>null</code> if the property value was not immediately available
     */
    @Nullable
    String getProperty(@NonNull String name);

    /**
     * Returns whether this device supports the given software feature
     *
     * @param feature feature
     * @return support or not
     */
    boolean supportsFeature(@NonNull Feature feature);

    /**
     * Returns whether this device supports the given hardware feature.
     *
     * @param feature feature
     * @return support or not
     */
    boolean supportsFeature(@NonNull HardwareFeature feature);

    /**
     * Returns a mount point.
     *
     * @param name the name of the mount point to return
     * @return mount point
     * @see #MNT_EXTERNAL_STORAGE
     * @see #MNT_ROOT
     * @see #MNT_DATA
     */
    @Nullable
    String getMountPoint(@NonNull String name);

    /**
     * Returns if the device is ready.
     *
     * @return <code>true</code> if {@link #getState()} returns {@link DeviceState#ONLINE}.
     */
    boolean isOnline();

    /**
     * Returns <code>true</code> if the device is an emulator.
     *
     * @return true if the device is an emulator
     */
    boolean isEmulator();

    /**
     * Returns if the device is offline.
     *
     * @return <code>true</code> if {@link #getState()} returns {@link DeviceState#OFFLINE}.
     */
    boolean isOffline();

    /**
     * Returns if the device is in bootloader mode.
     *
     * @return <code>true</code> if {@link #getState()} returns {@link DeviceState#BOOTLOADER}.
     */
    boolean isBootLoader();

    /**
     * Returns whether the {@link Device} has {@link Client}s.
     *
     * @return whether the {@link Device} has {@link Client}s
     */
    boolean hasClients();

    /**
     * Returns the array of clients.
     *
     * @return array of clients
     */
    Client[] getClients();

    /**
     * Returns a {@link Client} by its application name.
     *
     * @param applicationName the name of the application
     * @return the <code>Client</code> object or <code>null</code> if no match was found.
     */
    Client getClient(String applicationName);

    /**
     * Returns a {@link SyncService} object to push / pull files to and from the device.
     *
     * @return <code>null</code> if the SyncService couldn't be created. This can happen if adb
     * refuse to open the connection because the {@link IDevice} is invalid (or got disconnected).
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException if the connection with adb failed.
     */
    SyncService getSyncService()
        throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Returns a {@link FileListingService} for this device.
     *
     * @return {@link FileListingService} for this device
     */
    FileListingService getFileListingService();

    /**
     * Takes a screen shot of the device and returns it as a {@link RawImage}.
     *
     * @return the screenshot as a <code>RawImage</code> or <code>null</code> if something went wrong.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    RawImage getScreenshot() throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Get screenshot
     *
     * @param timeout timeout
     * @param unit timeout unit
     * @return image
     * @throws TimeoutException TimeoutException
     * @throws AdbCommandRejectedException AdbCommandRejectedException
     * @throws IOException IOException
     */
    RawImage getScreenshot(long timeout, TimeUnit unit)
        throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Initiates screen recording on the device if the device supports {@link Feature#SCREEN_RECORD}.
     *
     * @param remoteFilePath storage path
     * @param options options
     * @param receiver receiver
     * @throws TimeoutException TimeoutException
     * @throws AdbCommandRejectedException AdbCommandRejectedException
     * @throws IOException IOException
     * @throws ShellCommandUnresponsiveException ShellCommandUnresponsiveException
     */
    void startScreenRecorder(@NonNull String remoteFilePath,
        @NonNull ScreenRecorderOptions options, @NonNull IShellOutputReceiver receiver) throws
        TimeoutException, AdbCommandRejectedException, IOException,
        ShellCommandUnresponsiveException;

    /**
     * Executes a shell command on the device, and sends the result to a <var>receiver</var>
     * <p>This is similar to calling
     * <code>executeShellCommand(command, receiver, DdmPreferences.getTimeOut())</code>.
     *
     * @param command the shell command to execute
     * @param receiver the {@link IShellOutputReceiver} that will receives the output of the shell command
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output for a given time.
     * @throws IOException in case of I/O error on the connection.
     * @see DdmPreferences#getTimeOut()
     */
    void executeShellCommand(String command, IShellOutputReceiver receiver)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException;

    /**
     * Execute a command, and finally return a {@link ByteBuf} which contains all stdout and stderr data
     *
     * @param command command
     * @param timeout timeout
     * @param timeUnit timeout unit
     * @return all result data in a ByteBuf
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output for a given time.
     * @throws IOException in case of I/O error on the connection.
     */
    ByteBuf executeShellCommand(String command, long timeout, TimeUnit timeUnit) throws TimeoutException,
        AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException;

    /**
     * A version of executeShell command that can take an input stream to send through stdin.
     *
     * @param command the shell command to execute
     * @param receiver the {@link IShellOutputReceiver} that will receives the output of the shell command
     * @param maxTimeToOutputResponse timeout
     * @param maxTimeUnits timeout unit
     * @param is stdin
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output for a given time.
     * @throws IOException in case of I/O error on the connection.
     */
    void executeShellCommand(
        String command,
        IShellOutputReceiver receiver,
        long maxTimeToOutputResponse,
        TimeUnit maxTimeUnits,
        @Nullable InputStream is)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException;

    /**
     * execute command with a single line result
     *
     * @param command command
     * @return result
     * @throws TimeoutException TimeoutException
     * @throws AdbCommandRejectedException AdbCommandRejectedException
     * @throws ShellCommandUnresponsiveException ShellCommandUnresponsiveException
     * @throws IOException IOException
     */
    String execute(
        String command) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException;

    /**
     * execute command with a single line result
     *
     * @param command command
     * @param timeout timeout
     * @param timeUnit timeout unit
     * @return result
     * @throws TimeoutException TimeoutException
     * @throws AdbCommandRejectedException AdbCommandRejectedException
     * @throws ShellCommandUnresponsiveException ShellCommandUnresponsiveException
     * @throws IOException IOException
     */
    String execute(String command, Long timeout,
        TimeUnit timeUnit) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException;

    /**
     * Runs the event log service and outputs the event log to the {@link LogReceiver}.
     * <p>This call is blocking until {@link LogReceiver#isCancelled()} returns true.
     *
     * @param receiver the receiver to receive the event log entries.
     * @throws TimeoutException in case of timeout on the connection. This can only be thrown if the timeout happens
     * during setup. Once logs start being received, no timeout will occur as it's not possible to detect a difference
     * between no log and timeout.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    void runEventLogService(LogReceiver receiver)
        throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Runs the log service for the given log and outputs the log to the {@link LogReceiver}.
     * <p>This call is blocking until {@link LogReceiver#isCancelled()} returns true.
     *
     * @param logname the logname of the log to read from.
     * @param receiver the receiver to receive the event log entries.
     * @throws TimeoutException in case of timeout on the connection. This can only be thrown if the timeout happens
     * during setup. Once logs start being received, no timeout will occur as it's not possible to detect a difference
     * between no log and timeout.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    void runLogService(String logname, LogReceiver receiver)
        throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Creates a port forwarding between a local and a remote port.
     *
     * @param localPort the local port to forward
     * @param remotePort the remote port.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    void createForward(int localPort, int remotePort)
        throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Creates a port forwarding between a local TCP port and a remote Unix Domain Socket.
     *
     * @param localPort the local port to forward
     * @param remoteSocketName name of the unix domain socket created on the device
     * @param namespace namespace in which the unix domain socket was created
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    void createForward(int localPort, String remoteSocketName,
        DeviceUnixSocketNamespace namespace)
        throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Creates a port forwarding between a local TCP port and a remote Unix Domain Socket.
     *
     * @param unixSocket the local unix socket to forward
     * @param remoteSocketName name of the unix domain socket created on the device
     * @param namespace namespace in which the unix domain socket was created
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    void createForward(String unixSocket, String remoteSocketName,
        DeviceUnixSocketNamespace namespace)
        throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Removes a port forwarding between a local and a remote port.
     *
     * @param localPort the local port to forward
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    void removeForward(int localPort)
        throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Removes an existing port forwarding between a local and a remote port.
     *
     * @param unixSocket the local unix socket to forward
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    void removeForward(String unixSocket)
        throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Returns the name of the client by pid or <code>null</code> if pid is unknown
     *
     * @param pid the pid of the client.
     * @return client name
     */
    String getClientName(int pid);

    /**
     * Push a single file.
     *
     * @param local the local filepath.
     * @param remote The remote filepath.
     * @throws IOException in case of I/O error on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws TimeoutException in case of a timeout reading responses from the device.
     * @throws SyncException if file could not be pushed
     */
    void pushFile(String local, String remote)
        throws IOException, AdbCommandRejectedException, TimeoutException, SyncException;

    /**
     * Push a single file.
     *
     * @param localStream the local stream.
     * @param remote The remote filepath.
     * @param mode the file permission mode
     * @throws IOException in case of I/O error on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws TimeoutException in case of a timeout reading responses from the device.
     * @throws SyncException if file could not be pushed
     */
    void pushFile(InputStream localStream, String remote, int mode)
        throws IOException, AdbCommandRejectedException, TimeoutException, SyncException;

    /**
     * Pulls a single file.
     *
     * @param remote the full path to the remote file
     * @param local The local destination.
     * @throws IOException in case of an IO exception.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws TimeoutException in case of a timeout reading responses from the device.
     * @throws SyncException in case of a sync exception.
     */
    void pullFile(String remote, String local)
        throws IOException, AdbCommandRejectedException, TimeoutException, SyncException;

    /**
     * Check file same with a stream
     * @implNote stream shouldn't be empty, at least one byte could read
     *
     * @param remote the full path to the remote file
     * @param local The local stream
     * @return true if same
     */
    boolean isSameWithFile(String remote, InputStream local);

    /**
     * Installs an Android application on device. This is a helper method that combines the syncPackageToDevice,
     * installRemotePackage, and removePackage steps
     *
     * @param packageFilePath the absolute file system path to file on local host to install
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @return install time
     * @throws InstallException if the installation fails.
     */
    long installPackage(String packageFilePath, boolean reinstall, String... extraArgs)
        throws InstallException;

    /**
     * Install app by input stream
     *
     * @param inputStream the file stream on local host to install
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @throws InstallException if the installation fails.
     */
    void installPackage(InputStream inputStream, boolean reinstall,
        String... extraArgs) throws InstallException;

    /**
     * Installs an Android application on device. This is a helper method that combines the syncPackageToDevice,
     * installRemotePackage, and removePackage steps
     *
     * @param packageFilePath the absolute file system path to file on local host to install
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param receiver The {@link InstallReceiver} to be used to monitor the install and get final status.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @return install time
     * @throws InstallException if the installation fails.
     */
    long installPackage(
        String packageFilePath,
        boolean reinstall,
        InstallReceiver receiver,
        String... extraArgs)
        throws InstallException;

    /**
     * Installs an Android application on device. This is a helper method that combines the syncPackageToDevice,
     * installRemotePackage, and removePackage steps
     *
     * @param packageFilePath the absolute file system path to file on local host to install
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param receiver The {@link InstallReceiver} to be used to monitor the install and get final status.
     * @param maxTimeout the maximum timeout for the command to return. A value of 0 means no max timeout will be
     * applied.
     * @param maxTimeToOutputResponse the maximum amount of time during which the command is allowed to not output any
     * response. A value of 0 means the method will wait forever (until the
     * <var>receiver</var> cancels the execution) for command output and never throw.
     * @param maxTimeUnits Units for non-zero {@code maxTimeout} and {@code maxTimeToOutputResponse} values.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @return install time
     * @throws InstallException if the installation fails.
     */
    long installPackage(
        String packageFilePath,
        boolean reinstall,
        InstallReceiver receiver,
        long maxTimeout,
        long maxTimeToOutputResponse,
        TimeUnit maxTimeUnits,
        String... extraArgs)
        throws InstallException;

    /**
     * Installs an Android application made of several APK files (one main and 0..n split packages)
     *
     * @param apks list of apks to install (1 main APK + 0..n split apks)
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param installOptions optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @param timeout installation timeout
     * @param timeoutUnit {@link TimeUnit} corresponding to the timeout parameter
     * @throws InstallException if the installation fails.
     */
    void installPackages(
        @NonNull List<File> apks,
        boolean reinstall,
        @NonNull List<String> installOptions,
        long timeout,
        @NonNull TimeUnit timeoutUnit)
        throws InstallException;

    /**
     * Installs an Android application made of several APK files (one main and 0..n split packages) with default
     * timeout
     *
     * @param apks list of apks to install (1 main APK + 0..n split apks)
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param installOptions optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @throws InstallException if the installation fails.
     */
    default void installPackages(
        @NonNull List<File> apks, boolean reinstall, @NonNull List<String> installOptions)
        throws InstallException {
        throw new UnsupportedOperationException();
    }

    /**
     * Installs an Android application made of several APK files sitting locally on the device
     *
     * @param remoteApks list of apk file paths sitting on the device to install
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param installOptions optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @param timeout installation timeout
     * @param timeoutUnit {@link TimeUnit} corresponding to the timeout parameter
     * @throws InstallException if the installation fails.
     */
    default void installRemotePackages(
        @NonNull List<String> remoteApks,
        boolean reinstall,
        @NonNull List<String> installOptions,
        long timeout,
        @NonNull TimeUnit timeoutUnit)
        throws InstallException {
        throw new UnsupportedOperationException();
    }

    /**
     * Installs an Android application made of several APK files sitting locally on the device with default timeout
     *
     * @param remoteApks list of apk file paths on the device to install
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param installOptions optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @throws InstallException if the installation fails.
     */
    default void installRemotePackages(
        @NonNull List<String> remoteApks,
        boolean reinstall,
        @NonNull List<String> installOptions)
        throws InstallException {
        throw new UnsupportedOperationException();
    }

    /**
     * Pushes a file to device
     *
     * @param localFilePath the absolute path to file on local host
     * @return {@link String} destination path on device for file
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     * @throws SyncException if an error happens during the push of the package on the device.
     */
    String syncPackageToDevice(String localFilePath)
        throws TimeoutException, AdbCommandRejectedException, IOException, SyncException;

    /**
     * Pushes a file to device
     *
     * @param localFileStream file input stream on local host
     * @return {@link String} destination path on device for file
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     * @throws SyncException if an error happens during the push of the package on the device.
     */
    String syncPackageToDevice(InputStream localFileStream)
        throws TimeoutException, AdbCommandRejectedException, IOException, SyncException;

    /**
     * Installs the application package that was pushed to a temporary location on the device.
     *
     * @param remoteFilePath absolute file path to package file on device
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @throws InstallException if the installation fails.
     * @see #installRemotePackage(String, boolean, InstallReceiver, long, long, TimeUnit, String...)
     */
    void installRemotePackage(String remoteFilePath, boolean reinstall, String... extraArgs)
        throws InstallException;

    /**
     * Installs the application package that was pushed to a temporary location on the device.
     *
     * @param remoteFilePath absolute file path to package file on device
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param receiver The {@link InstallReceiver} to be used to monitor the install and get final status.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @throws InstallException if the installation fails.
     * @see #installRemotePackage(String, boolean, InstallReceiver, long, long, TimeUnit, String...)
     */
    void installRemotePackage(
        String remoteFilePath, boolean reinstall, InstallReceiver receiver, String... extraArgs)
        throws InstallException;

    /**
     * Installs the application package that was pushed to a temporary location on the device.
     *
     * @param remoteFilePath absolute file path to package file on device
     * @param reinstall set to <code>true</code> if re-install of app should be performed
     * @param receiver The {@link InstallReceiver} to be used to monitor the install and get final status.
     * @param maxTimeout the maximum timeout for the command to return. A value of 0 means no max timeout will be
     * applied.
     * @param maxTimeToOutputResponse the maximum amount of time during which the command is allowed to not output any
     * response. A value of 0 means the method will wait forever (until the
     * <var>receiver</var> cancels the execution) for command output and never throw.
     * @param maxTimeUnits Units for non-zero {@code maxTimeout} and {@code maxTimeToOutputResponse} values.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for available options.
     * @return install time
     * @throws InstallException if the installation fails.
     */
    long installRemotePackage(
        String remoteFilePath,
        boolean reinstall,
        InstallReceiver receiver,
        long maxTimeout,
        long maxTimeToOutputResponse,
        TimeUnit maxTimeUnits,
        String... extraArgs)
        throws InstallException;

    /**
     * Removes a file from device.
     *
     * @param remoteFilePath path on device of file to remove
     * @throws InstallException if the installation fails.
     */
    void removeRemotePackage(String remoteFilePath) throws InstallException;

    /**
     * Uninstalls an package from the device.
     *
     * @param packageName the Android application package name to uninstall
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws InstallException if the uninstallation fails.
     */
    String uninstallPackage(String packageName) throws InstallException;

    /**
     * Reboot the device.
     *
     * @param into the bootloader name to reboot into, or null to just reboot the device.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException io exception
     */
    void reboot(String into)
        throws TimeoutException, AdbCommandRejectedException, IOException;

    /**
     * Ask the adb daemon to become root on the device. This may silently fail, and can only succeed on developer
     * builds. See "adb root" for more information.
     *
     * @return true if the adb daemon is running as root, otherwise false.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command.
     * @throws ShellCommandUnresponsiveException if the root status cannot be queried.
     * @throws IOException io exception
     */
    boolean root() throws TimeoutException, AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException;

    /**
     * Queries the current root-status of the device. See "adb root" for more information.
     *
     * @return true if the adb daemon is running as root, otherwise false.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command.
     * @throws IOException io exception
     * @throws ShellCommandUnresponsiveException shell exception
     */
    boolean isRoot() throws TimeoutException, AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException;

    /**
     * Return the device's battery level, from 0 to 100 percent.
     * <p>
     * The battery level may be cached. Only queries the device for its battery level if 5 minutes have expired since
     * the last successful query.
     *
     * @return a {@link Future} that can be used to query the battery level. The Future will return a {@link
     * ExecutionException} if battery level could not be retrieved.
     */
    @NonNull
    Future<Integer> getBattery();

    /**
     * Return the device's battery level, from 0 to 100 percent.
     * <p>
     * The battery level may be cached. Only queries the device for its battery level if <code>freshnessTime</code> has
     * expired since the last successful query.
     *
     * @param freshnessTime the desired recency of battery level
     * @param timeUnit the {@link TimeUnit} of freshnessTime
     * @return a {@link Future} that can be used to query the battery level. The Future will return a {@link
     * ExecutionException} if battery level could not be retrieved.
     */
    @NonNull
    Future<Integer> getBattery(long freshnessTime, @NonNull TimeUnit timeUnit);

    /**
     * Returns the ABIs supported by this device. The ABIs are sorted in preferred order, with the first ABI being the
     * most preferred.
     *
     * @return the list of ABIs.
     */
    @NonNull
    List<String> getAbis();

    /**
     * Returns the density bucket of the device screen by reading the value for system property {@link
     * #PROP_DEVICE_DENSITY}.
     *
     * @return the density, or -1 if it cannot be determined.
     */
    int getDensity();

    /**
     * Returns the user's language.
     *
     * @return the user's language, or null if it's unknown
     */
    @Nullable
    String getLanguage();

    /**
     * Returns the user's region.
     *
     * @return the user's region, or null if it's unknown
     */
    @Nullable
    String getRegion();

    /**
     * Returns the API level of the device.
     *
     * @return the API level if it can be determined, {@link AndroidVersion#DEFAULT} otherwise.
     */
    @NonNull
    AndroidVersion getVersion();

    /**
     * Set input method
     *
     * @param ime input method name
     * @throws TimeoutException TimeoutException
     * @throws AdbCommandRejectedException AdbCommandRejectedException
     * @throws ShellCommandUnresponsiveException ShellCommandUnresponsiveException
     * @throws IOException IOException
     */
    void setIme(
        String ime) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException;

    /**
     * Get app version of specific package
     *
     * @param packageName package name
     * @return version in string
     * @throws TimeoutException TimeoutException
     * @throws AdbCommandRejectedException AdbCommandRejectedException
     * @throws ShellCommandUnresponsiveException ShellCommandUnresponsiveException
     * @throws IOException IOException
     */
    String getPackageVersionName(
        String packageName) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException;
}
