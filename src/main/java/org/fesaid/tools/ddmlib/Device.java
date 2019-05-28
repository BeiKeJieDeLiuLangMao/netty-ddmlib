package org.fesaid.tools.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Atomics;
import io.netty.buffer.ByteBuf;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.log.LogReceiver;
import org.fesaid.tools.ddmlib.netty.AdbConnection;

import static com.android.sdklib.AndroidVersion.VersionCodes.KITKAT;

/**
 * A Device. It can be a physical device or an emulator.
 */
@Slf4j final class Device implements IDevice {
    /** Emulator Serial Number regexp. */
    static final String RE_EMULATOR_SN = "emulator-(\\d+)";
    private static final String ZERO = "0";
    private static final int SCREEN_RECORDER_MAX_TIME_LIMIT = 180;

    /** Serial number of the device */
    private final String mSerialNumber;

    /** Name of the AVD */
    private String mAvdName = null;

    /** State of the device. */
    private DeviceState mState;

    /** True if ADB is running as root */
    private boolean mIsRoot = false;

    /** Device properties. */
    private final PropertyFetcher mPropFetcher = new PropertyFetcher(this);
    private final Map<String, String> mMountPoints = new HashMap<>();

    private final BatteryFetcher mBatteryFetcher = new BatteryFetcher(this);

    @GuardedBy("mClients")
    private final List<Client> mClients = new ArrayList<>();

    /** Maps pid's of clients in {@link #mClients} to their package name. */
    private final Map<Integer, String> mClientInfo = new ConcurrentHashMap<>();

    private ClientTracker mClientTracer;

    private static final char SEPARATOR = '-';
    private static final String UNKNOWN_PACKAGE = "";

    private static final long GET_PROP_TIMEOUT_MS = 250;
    private static final long INITIAL_GET_PROP_TIMEOUT_MS = 2000;
    private static final int QUERY_IS_ROOT_TIMEOUT_MS = 1000;

    private static final long INSTALL_TIMEOUT_MINUTES;

    static {
        String installTimeout = System.getenv("ADB_INSTALL_TIMEOUT");
        long time = 4;
        if (installTimeout != null) {
            try {
                time = Long.parseLong(installTimeout);
            } catch (NumberFormatException e) {
                // use default value
            }
        }
        INSTALL_TIMEOUT_MINUTES = time;
    }

    /**
     * Socket for the connection monitoring client connection/disconnection.
     */
    private AdbConnection mSocketChannel;

    /** Path to the screen recorder binary on the device. */
    private static final String SCREEN_RECORDER_DEVICE_PATH = "/system/bin/screenrecord";
    private static final long LS_TIMEOUT_SEC = 2;

    /** Flag indicating whether the device has the screen recorder binary. */
    private Boolean mHasScreenRecorder;

    /** Cached list of hardware characteristics */
    private Set<String> mHardwareCharacteristics;

    @Nullable private AndroidVersion mVersion;
    private String mName;

    @NonNull
    @Override
    public String getSerialNumber() {
        return mSerialNumber;
    }

    @Override
    public String getAvdName() {
        return mAvdName;
    }

    /**
     * Sets the name of the AVD
     */
    void setAvdName(String avdName) {
        if (!isEmulator()) {
            throw new IllegalArgumentException(
                "Cannot set the AVD name of the device is not an emulator");
        }

        mAvdName = avdName;
    }

    @Override
    public String getName() {
        if (mName != null) {
            return mName;
        }

        if (isOnline()) {
            // cache name only if device is online
            mName = constructName();
            return mName;
        } else {
            return constructName();
        }
    }

    private String constructName() {
        if (isEmulator()) {
            String avdName = getAvdName();
            if (avdName != null) {
                return String.format("%s [%s]", avdName, getSerialNumber());
            } else {
                return getSerialNumber();
            }
        } else {
            String manufacturer = null;
            String model = null;

            try {
                manufacturer = cleanupStringForDisplay(getProperty(PROP_DEVICE_MANUFACTURER));
                model = cleanupStringForDisplay(getProperty(PROP_DEVICE_MODEL));
            } catch (Exception e) {
                // If there are exceptions thrown while attempting to get these properties,
                // we can just use the serial number, so ignore these exceptions.
            }

            StringBuilder sb = new StringBuilder(20);

            if (manufacturer != null) {
                sb.append(manufacturer);
                sb.append(SEPARATOR);
            }

            if (model != null) {
                sb.append(model);
                sb.append(SEPARATOR);
            }

            sb.append(getSerialNumber());
            return sb.toString();
        }
    }

    private static String cleanupStringForDisplay(String s) {
        if (s == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append('_');
            }
        }

        return sb.toString();
    }

    @Override
    public DeviceState getState() {
        return mState;
    }

    /**
     * Changes the state of the device.
     */
    void setState(DeviceState state) {
        mState = state;
    }

    @Override
    public String getProperty(@NonNull String name) {
        Map<String, String> properties = mPropFetcher.getProperties();
        long timeout = properties.isEmpty() ? INITIAL_GET_PROP_TIMEOUT_MS : GET_PROP_TIMEOUT_MS;

        Future<String> future = mPropFetcher.getProperty(name);
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException
            | ExecutionException
            | java.util.concurrent.TimeoutException e) {
            // ignore
        }
        return null;
    }

    @NonNull
    @Override
    public Future<String> getSystemProperty(@NonNull String name) {
        return mPropFetcher.getProperty(name);
    }

    @Override
    public boolean supportsFeature(@NonNull Feature feature) {
        switch (feature) {
            case SCREEN_RECORD:
                if (!getVersion().isGreaterOrEqualThan(KITKAT)) {
                    return false;
                }
                if (mHasScreenRecorder == null) {
                    mHasScreenRecorder = hasBinary();
                }
                return mHasScreenRecorder;
            case PROCSTATS:
                return getVersion().isGreaterOrEqualThan(KITKAT);
            default:
                return false;
        }
    }

    /**
     * The full list of features can be obtained from /etc/permissions/features* However, the smaller set of features we
     * are interested in can be obtained by reading the build characteristics property.
     */
    @Override
    public boolean supportsFeature(@NonNull HardwareFeature feature) {
        if (mHardwareCharacteristics == null) {
            try {
                String characteristics = getProperty(PROP_BUILD_CHARACTERISTICS);
                if (characteristics == null) {
                    return false;
                }

                mHardwareCharacteristics = Sets.newHashSet(Splitter.on(',').split(characteristics));
            } catch (Exception e) {
                mHardwareCharacteristics = Collections.emptySet();
            }
        }

        return mHardwareCharacteristics.contains(feature.getCharacteristic());
    }

    @NonNull
    @Override
    public AndroidVersion getVersion() {
        if (mVersion != null) {
            return mVersion;
        }

        try {
            String buildApi = getProperty(PROP_BUILD_API_LEVEL);
            if (buildApi == null) {
                return AndroidVersion.DEFAULT;
            }

            int api = Integer.parseInt(buildApi);
            String codeName = getProperty(PROP_BUILD_CODENAME);
            mVersion = new AndroidVersion(api, codeName);
            return mVersion;
        } catch (Exception e) {
            return AndroidVersion.DEFAULT;
        }
    }

    @Override
    public void setIme(
        String ime) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        this.execute("ime set " + ime, 20L, TimeUnit.SECONDS);
    }

    @Override
    public String getPackageVersionName(
        String packageName) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        PackageVersionNameReceiver receiver = new PackageVersionNameReceiver(packageName);
        executeShellCommand("dumpsys package " + packageName, receiver, 20, TimeUnit.SECONDS);
        return receiver.getVersionName();
    }

    private boolean hasBinary() {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
        try {
            executeShellCommand("ls " + Device.SCREEN_RECORDER_DEVICE_PATH, receiver, LS_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }

        try {
            latch.await(LS_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }

        String value = receiver.getOutput().trim();
        return !value.endsWith("No such file or directory");
    }

    @Nullable
    @Override
    public String getMountPoint(@NonNull String name) {
        String mount = mMountPoints.get(name);
        if (mount == null) {
            try {
                mount = queryMountPoint(name);
                mMountPoints.put(name, mount);
            } catch (TimeoutException
                | AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | IOException ignored) {
            }
        }
        return mount;
    }

    @Nullable
    private String queryMountPoint(@NonNull final String name)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException {

        final AtomicReference<String> ref = Atomics.newReference();
        executeShellCommand(
            "echo $" + name,
            new MultiLineReceiver() {
                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public void processNewLines(@NonNull String[] lines) {
                    for (String line : lines) {
                        if (!line.isEmpty()) {
                            // this should be the only one.
                            ref.set(line);
                        }
                    }
                }
            });
        return ref.get();
    }

    @Override
    public String toString() {
        return mSerialNumber;
    }

    @Override
    public boolean isOnline() {
        return mState == DeviceState.ONLINE;
    }

    @Override
    public boolean isEmulator() {
        return mSerialNumber.matches(RE_EMULATOR_SN);
    }

    @Override
    public boolean isOffline() {
        return mState == DeviceState.OFFLINE;
    }

    @Override
    public boolean isBootLoader() {
        return mState == DeviceState.BOOTLOADER;
    }

    @Override
    public SyncService getSyncService()
        throws TimeoutException, AdbCommandRejectedException, IOException {
        SyncService syncService = new SyncService(AndroidDebugBridge.getSocketAddress(), this);
        if (syncService.openSync()) {
            return syncService;
        }
        return null;
    }

    @Override
    public FileListingService getFileListingService() {
        return new FileListingService(this);
    }

    @Override
    public RawImage getScreenshot()
        throws TimeoutException, AdbCommandRejectedException, IOException {
        return getScreenshot(0, TimeUnit.MILLISECONDS);
    }

    @Override
    public RawImage getScreenshot(long timeout, TimeUnit unit)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        return AdbHelper.getFrameBuffer(AndroidDebugBridge.getSocketAddress(), this, timeout, unit);
    }

    @Override
    public void startScreenRecorder(
        @NonNull String remoteFilePath,
        @NonNull ScreenRecorderOptions options,
        @NonNull IShellOutputReceiver receiver) throws TimeoutException,
        AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException {
        executeShellCommand(getScreenRecorderCommand(remoteFilePath, options), receiver, 0, null);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    private static String getScreenRecorderCommand(@NonNull String remoteFilePath,
        @NonNull ScreenRecorderOptions options) {
        StringBuilder sb = new StringBuilder();

        sb.append("screenrecord");
        sb.append(' ');

        if (options.width > 0 && options.height > 0) {
            sb.append("--size ");
            sb.append(options.width);
            sb.append('x');
            sb.append(options.height);
            sb.append(' ');
        }

        if (options.bitrateMbps > 0) {
            sb.append("--bit-rate ");
            sb.append(options.bitrateMbps * 1000000);
            sb.append(' ');
        }

        if (options.timeLimit > 0) {
            sb.append("--time-limit ");
            long seconds = TimeUnit.SECONDS.convert(options.timeLimit, options.timeLimitUnits);
            if (seconds > SCREEN_RECORDER_MAX_TIME_LIMIT) {
                seconds = SCREEN_RECORDER_MAX_TIME_LIMIT;
            }
            sb.append(seconds);
            sb.append(' ');
        }

        sb.append(remoteFilePath);

        return sb.toString();
    }

    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), command, this,
            receiver, DdmPreferences.getTimeOut(), TimeUnit.MILLISECONDS);
    }

    @Override
    public ByteBuf executeShellCommand(String command, long timeout, TimeUnit timeUnit) throws TimeoutException,
        AdbCommandRejectedException, IOException {
        return AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), command, this, timeout,
            timeUnit);
    }

    @Override
    public void executeShellCommand(
        String command,
        IShellOutputReceiver receiver,
        long maxTimeToOutputResponse,
        TimeUnit maxTimeUnits,
        @Nullable InputStream is)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException {
        AdbHelper.executeRemoteCommand(
            AndroidDebugBridge.getSocketAddress(),
            AdbHelper.AdbService.EXEC,
            command,
            this,
            receiver,
            0L,
            maxTimeToOutputResponse,
            maxTimeUnits,
            is);
    }

    @Override
    public String execute(
        String command) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        return execute(command, 10L, TimeUnit.SECONDS);
    }

    @Override public String execute(String command, Long timeout,
        TimeUnit timeUnit) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        SingleLineReceiver singleLineReceiver = new SingleLineReceiver();
        executeShellCommand(command, singleLineReceiver);
        return singleLineReceiver.get(timeout, timeUnit);
    }

    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
        long maxTimeToOutputResponse, TimeUnit maxTimeUnits)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException {
        AdbHelper.executeRemoteCommand(
            AndroidDebugBridge.getSocketAddress(),
            command,
            this,
            receiver,
            0L,
            maxTimeToOutputResponse,
            maxTimeUnits);
    }

    @Override
    public void executeShellCommand(
        String command,
        IShellOutputReceiver receiver,
        long maxTimeout,
        long maxTimeToOutputResponse,
        TimeUnit maxTimeUnits)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException {
        AdbHelper.executeRemoteCommand(
            AndroidDebugBridge.getSocketAddress(),
            command,
            this,
            receiver,
            maxTimeout,
            maxTimeToOutputResponse,
            maxTimeUnits);
    }

    @Override
    public void runEventLogService(LogReceiver receiver)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.runEventLogService(AndroidDebugBridge.getSocketAddress(), this, receiver);
    }

    @Override
    public void runLogService(String logname, LogReceiver receiver)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.runLogService(AndroidDebugBridge.getSocketAddress(), this, logname, receiver);
    }

    @Override
    public void createForward(int localPort, int remotePort)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.createForward(AndroidDebugBridge.getSocketAddress(), this,
            String.format("tcp:%d", localPort),
            String.format("tcp:%d", remotePort));
    }

    @Override
    public void createForward(int localPort, String remoteSocketName,
        DeviceUnixSocketNamespace namespace) throws TimeoutException,
        AdbCommandRejectedException, IOException {
        AdbHelper.createForward(AndroidDebugBridge.getSocketAddress(), this,
            String.format("tcp:%d", localPort),
            String.format("%s:%s", namespace.getType(), remoteSocketName));
    }

    @Override
    public void createForward(String unixSocket, String remoteSocketName,
        DeviceUnixSocketNamespace namespace) throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.createForward(AndroidDebugBridge.getSocketAddress(), this,
            String.format("localfilesystem:%s", unixSocket),
            String.format("%s:%s", namespace.getType(), remoteSocketName));
    }

    @Override
    public void removeForward(int localPort) throws TimeoutException,
        AdbCommandRejectedException, IOException {
        AdbHelper.removeForward(AndroidDebugBridge.getSocketAddress(), this,
            String.format("tcp:%d", localPort));
    }

    @Override
    public void removeForward(String unixSocket) throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.removeForward(AndroidDebugBridge.getSocketAddress(), this,
            String.format("localfilesystem:%s", unixSocket));
    }

    Device(ClientTracker clientTracer, String serialNumber, DeviceState deviceState) {
        mClientTracer = clientTracer;
        mSerialNumber = serialNumber;
        mState = deviceState;
    }

    ClientTracker getClientTracker() {
        return mClientTracer;
    }

    @Override
    public boolean hasClients() {
        synchronized (mClients) {
            return !mClients.isEmpty();
        }
    }

    @Override
    public Client[] getClients() {
        synchronized (mClients) {
            return mClients.toArray(new Client[0]);
        }
    }

    @Override
    public Client getClient(String applicationName) {
        synchronized (mClients) {
            for (Client c : mClients) {
                if (applicationName.equals(c.getClientData().getClientDescription())) {
                    return c;
                }
            }
        }

        return null;
    }

    void addClient(Client client) {
        synchronized (mClients) {
            mClients.add(client);
        }

        addClientInfo(client);
    }

    List<Client> getClientList() {
        synchronized (mClients) {
            return mClients;
        }
    }

    void clearClientList() {
        synchronized (mClients) {
            mClients.clear();
        }

        clearClientInfo();
    }

    /**
     * Removes a {@link Client} from the list.
     *
     * @param client the client to remove.
     * @param notify Whether or not to notify the listeners of a change.
     */
    void removeClient(Client client, boolean notify) {
        mClientTracer.trackDisconnectedClient(client);
        synchronized (mClients) {
            mClients.remove(client);
        }
        if (notify) {
            AndroidDebugBridge.deviceChanged(this, CHANGE_CLIENT_LIST);
        }

        removeClientInfo(client);
    }

    /** Sets the socket channel on which a track-jdwp command for this device has been sent. */
    void setClientMonitoringSocket(@NonNull AdbConnection socketChannel) {
        mSocketChannel = socketChannel;
    }

    /**
     * Returns the channel on which responses to the track-jdwp command will be available if it has been set, null
     * otherwise. The channel is set via {@link #setClientMonitoringSocket(AdbConnection)}, which is usually invoked
     * when the device goes online.
     */
    @Nullable
    AdbConnection getClientMonitoringSocket() {
        return mSocketChannel;
    }

    void update(int changeMask) {
        AndroidDebugBridge.deviceChanged(this, changeMask);
    }

    void update(@NonNull Client client, int changeMask) {
        AndroidDebugBridge.clientChanged(client, changeMask);
        updateClientInfo(client, changeMask);
    }

    private void addClientInfo(Client client) {
        ClientData cd = client.getClientData();
        setClientInfo(cd.getPid(), cd.getClientDescription());
    }

    private void updateClientInfo(Client client, int changeMask) {
        if ((changeMask & Client.CHANGE_NAME) == Client.CHANGE_NAME) {
            addClientInfo(client);
        }
    }

    private void removeClientInfo(Client client) {
        int pid = client.getClientData().getPid();
        mClientInfo.remove(pid);
    }

    private void clearClientInfo() {
        mClientInfo.clear();
    }

    private void setClientInfo(int pid, String pkgName) {
        if (pkgName == null) {
            pkgName = UNKNOWN_PACKAGE;
        }

        mClientInfo.put(pid, pkgName);
    }

    @Override
    public String getClientName(int pid) {
        String pkgName = mClientInfo.get(pid);
        return pkgName == null ? UNKNOWN_PACKAGE : pkgName;
    }

    @Override
    public void pushFile(String local, String remote)
        throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        try (SyncService sync = getSyncService()) {
            String targetFileName = getFileName(local);
            log.debug(String.format("Uploading %1$s onto device '%2$s'", targetFileName, getSerialNumber()));
            if (sync != null) {
                sync.pushFile(local, remote, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        }
    }

    @Override
    public void pushFile(InputStream localStream, String remote,
        int mode) throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        try (SyncService sync = getSyncService()) {
            log.debug(String.format("Uploading stream onto device '%1$s'", getSerialNumber()));
            if (sync != null) {
                sync.pushFile(localStream, remote, SyncService.getNullProgressMonitor(), mode);
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        }
    }

    @Override
    public void pullFile(String remote, String local)
        throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        try (SyncService sync = getSyncService()) {
            String targetFileName = getFileName(remote);
            log.debug(String.format("Downloading %1$s from device '%2$s'", targetFileName, getSerialNumber()));
            if (sync != null) {
                sync.pullFile(remote, local, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        }
    }

    @Override
    public boolean isSameWithFile(String remote, InputStream local)  {
        try (SyncService sync = getSyncService()) {
            String targetFileName = getFileName(remote);
            log.debug(String.format("Downloading %1$s from device '%2$s'", targetFileName, getSerialNumber()));
            if (sync != null) {
                return sync.isSameWithFile(remote, local, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long installPackage(String packageFilePath, boolean reinstall,
        String... extraArgs)
        throws InstallException {
        // Use default basic installReceiver
        return installPackage(packageFilePath, reinstall, new InstallReceiver(), extraArgs);
    }

    @Override
    public void installPackage(InputStream inputStream, boolean reinstall,
        String... extraArgs) throws InstallException {
        // Use default basic installReceiver
        try {
            String remoteFilePath = syncPackageToDevice(inputStream);
            installRemotePackage(
                remoteFilePath,
                reinstall,
                new InstallReceiver(),
                0L,
                INSTALL_TIMEOUT_MINUTES,
                TimeUnit.MINUTES,
                extraArgs);
            removeRemotePackage(remoteFilePath);
        } catch (Exception e) {
            throw new InstallException(e);
        }
    }

    @Override
    public long installPackage(
        String packageFilePath,
        boolean reinstall,
        InstallReceiver receiver,
        String... extraArgs)
        throws InstallException {
        // Use default values for some timeouts.
        return installPackage(
            packageFilePath,
            reinstall,
            receiver,
            0L,
            INSTALL_TIMEOUT_MINUTES,
            TimeUnit.MINUTES,
            extraArgs);
    }

    @Override
    public long installPackage(
        String packageFilePath,
        boolean reinstall,
        InstallReceiver receiver,
        long maxTimeout,
        long maxTimeToOutputResponse,
        TimeUnit maxTimeUnits,
        String... extraArgs)
        throws InstallException {
        try {
            String remoteFilePath = syncPackageToDevice(packageFilePath);
            long time = installRemotePackage(
                remoteFilePath,
                reinstall,
                receiver,
                maxTimeout,
                maxTimeToOutputResponse,
                maxTimeUnits,
                extraArgs);
            removeRemotePackage(remoteFilePath);
            return time;
        } catch (IOException | AdbCommandRejectedException | TimeoutException | SyncException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public void installPackages(
        @NonNull List<File> apks,
        boolean reinstall,
        @NonNull List<String> installOptions,
        long timeout,
        @NonNull TimeUnit timeoutUnit)
        throws InstallException {
        try {
            SplitApkInstaller.create(this, apks, reinstall, installOptions)
                .install(timeout, timeoutUnit);
        } catch (InstallException e) {
            throw e;
        } catch (Exception e) {
            throw new InstallException(e);
        }
    }

    @Override
    public void installPackages(
        @NonNull List<File> apks, boolean reinstall, @NonNull List<String> installOptions)
        throws InstallException {
        // Use the default single apk installer timeout.
        installPackages(apks, reinstall, installOptions, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void installRemotePackages(
        @NonNull List<String> remoteApks,
        boolean reinstall,
        @NonNull List<String> installOptions)
        throws InstallException {
        // Use the default installer timeout.
        installRemotePackages(
            remoteApks, reinstall, installOptions, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void installRemotePackages(
        @NonNull List<String> remoteApks,
        boolean reinstall,
        @NonNull List<String> installOptions,
        long timeout,
        @NonNull TimeUnit timeoutUnit)
        throws InstallException {
        try {
            RemoteSplitApkInstaller.create(this, remoteApks, reinstall, installOptions)
                .install(timeout, timeoutUnit);
        } catch (InstallException e) {
            throw e;
        } catch (Exception e) {
            throw new InstallException(e);
        }
    }

    @Override
    public String syncPackageToDevice(String localFilePath)
        throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        try (SyncService sync = getSyncService()) {
            String packageFileName = getFileName(localFilePath);
            String remoteFilePath = String.format("/data/local/tmp/%1$s", packageFileName);
            log.debug(String.format("Uploading %1$s onto device '%2$s'", packageFileName, getSerialNumber()));
            if (sync != null) {
                sync.pushFile(localFilePath, remoteFilePath, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
            return remoteFilePath;
        }
    }

    @Override
    public String syncPackageToDevice(InputStream localFileStream) throws TimeoutException, AdbCommandRejectedException,
        IOException, SyncException {
        try (SyncService sync = getSyncService()) {
            String packageFileName = localFileStream.hashCode() + "-" + ThreadLocalRandom.current().nextInt() + ".apk";
            String remoteFilePath = String.format("/data/local/tmp/%1$s", packageFileName);
            log.debug(String.format("Uploading %1$s onto device '%2$s'", packageFileName, getSerialNumber()));
            if (sync != null) {
                sync.pushFile(localFileStream, remoteFilePath, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
            return remoteFilePath;
        }
    }

    /**
     * Helper method to retrieve the file name given a local file path
     *
     * @param filePath full directory path to file
     * @return {@link String} file name
     */
    private static String getFileName(String filePath) {
        return new File(filePath).getName();
    }

    @Override
    public void installRemotePackage(String remoteFilePath, boolean reinstall,
        String... extraArgs) throws InstallException {
        installRemotePackage(remoteFilePath, reinstall, new InstallReceiver(), extraArgs);
    }

    @Override
    public void installRemotePackage(
        String remoteFilePath,
        boolean reinstall,
        @NonNull InstallReceiver receiver,
        String... extraArgs)
        throws InstallException {
        installRemotePackage(
            remoteFilePath,
            reinstall,
            receiver,
            0L,
            INSTALL_TIMEOUT_MINUTES,
            TimeUnit.MINUTES,
            extraArgs);
    }

    @Override
    public long installRemotePackage(
        String remoteFilePath,
        boolean reinstall,
        @NonNull InstallReceiver receiver,
        long maxTimeout,
        long maxTimeToOutputResponse,
        TimeUnit maxTimeUnits,
        String... extraArgs)
        throws InstallException {
        try {
            StringBuilder optionString = new StringBuilder();
            if (reinstall) {
                optionString.append("-r ");
            }
            if (extraArgs != null) {
                optionString.append(Joiner.on(' ').join(extraArgs));
            }
            String cmd = String.format("pm install %1$s \"%2$s\"", optionString.toString(),
                remoteFilePath);
            long start = System.currentTimeMillis();
            executeShellCommand(cmd, receiver, maxTimeout, maxTimeToOutputResponse, maxTimeUnits);
            String error = receiver.getErrorMessage();
            if (error != null) {
                throw new InstallException(error);
            }
            return System.currentTimeMillis() - start;
        } catch (TimeoutException
            | AdbCommandRejectedException
            | ShellCommandUnresponsiveException
            | IOException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public void removeRemotePackage(String remoteFilePath) throws InstallException {
        try {
            executeShellCommand(String.format("rm \"%1$s\"", remoteFilePath),
                new NullOutputReceiver(), INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (IOException
            | TimeoutException
            | AdbCommandRejectedException
            | ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public String uninstallPackage(String packageName) throws InstallException {
        try {
            InstallReceiver receiver = new InstallReceiver();
            executeShellCommand("pm uninstall " + packageName, receiver, INSTALL_TIMEOUT_MINUTES,
                TimeUnit.MINUTES);
            return receiver.getErrorMessage();
        } catch (TimeoutException
            | AdbCommandRejectedException
            | ShellCommandUnresponsiveException
            | IOException e) {
            throw new InstallException(e);
        }
    }

    /**
     * Reboot the device.
     *
     * @param into the bootloader name to reboot into, or null to just reboot the device.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    @Override
    public void reboot(String into)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.reboot(into, AndroidDebugBridge.getSocketAddress(), this);
    }

    @Override
    public boolean root()
        throws TimeoutException, AdbCommandRejectedException, IOException,
        ShellCommandUnresponsiveException {
        if (!mIsRoot) {
            AdbHelper.root(AndroidDebugBridge.getSocketAddress(), this);
        }
        return isRoot();
    }

    @Override
    public boolean isRoot()
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException {
        if (mIsRoot) {
            return true;
        }
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        executeShellCommand(
            "echo $USER_ID", receiver, QUERY_IS_ROOT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        String userID = receiver.getOutput().trim();
        mIsRoot = userID.equals(ZERO);
        return mIsRoot;
    }

    @NonNull
    @Override
    public Future<Integer> getBattery() {
        return getBattery(5, TimeUnit.MINUTES);
    }

    @NonNull
    @Override
    public Future<Integer> getBattery(long freshnessTime, @NonNull TimeUnit timeUnit) {
        return mBatteryFetcher.getBattery(freshnessTime, timeUnit);
    }

    @NonNull
    @Override
    public List<String> getAbis() {
        /* Try abiList (implemented in L onwards) otherwise fall back to abi and abi2. */
        String abiList = getProperty(IDevice.PROP_DEVICE_CPU_ABI_LIST);
        if (abiList != null) {
            return Lists.newArrayList(abiList.split(","));
        } else {
            List<String> abis = Lists.newArrayListWithExpectedSize(2);
            String abi = getProperty(IDevice.PROP_DEVICE_CPU_ABI);
            if (abi != null) {
                abis.add(abi);
            }

            abi = getProperty(IDevice.PROP_DEVICE_CPU_ABI2);
            if (abi != null) {
                abis.add(abi);
            }

            return abis;
        }
    }

    @Override
    public int getDensity() {
        String densityValue = getProperty(IDevice.PROP_DEVICE_DENSITY);
        if (densityValue == null) {
            densityValue = getProperty(IDevice.PROP_DEVICE_EMULATOR_DENSITY);
        }
        if (densityValue != null) {
            try {
                return Integer.parseInt(densityValue);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return -1;
    }

    @Override
    public String getLanguage() {
        return getProperty(IDevice.PROP_DEVICE_LANGUAGE);
    }

    @Override
    public String getRegion() {
        return getProperty(IDevice.PROP_DEVICE_REGION);
    }

}
