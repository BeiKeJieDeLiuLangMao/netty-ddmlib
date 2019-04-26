package org.fesaid.tools.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.ClientData.DebuggerStatus;
import org.fesaid.tools.ddmlib.DebugPortManager.IDebugPortProvider;
import org.fesaid.tools.ddmlib.jdwp.JdwpEvent;
import org.fesaid.tools.ddmlib.netty.AdbConnection;
import org.fesaid.tools.ddmlib.netty.input.AdbInputHandler;
import org.fesaid.tools.ddmlib.netty.input.DeviceMonitorHandler;
import org.fesaid.tools.ddmlib.utils.DebuggerPorts;

import static org.fesaid.tools.ddmlib.AdbHelper.connect;

/**
 * The {@link DeviceMonitor} monitors devices attached to adb.
 *
 * <p>On one thread, it runs the {@link DeviceListMonitorTask}.
 * This establishes a socket connection to the adb host, and issues a {@link #ADB_TRACK_DEVICES_COMMAND}. It then
 * monitors that socket for all changes about device connection and device state.
 *
 * <p>For each device that is detected to be online, it then opens a new socket connection to adb,
 * and issues a "track-jdwp" command to that device. On this connection, it monitors active clients on the device. Note:
 * a single thread monitors jdwp connections from all devices. The different socket connections to adb (one per device)
 * are multiplexed over a single selector.
 */
@SuppressWarnings("unused")
@Slf4j
final class DeviceMonitor implements ClientTracker {
    private static final String ADB_TRACK_DEVICES_COMMAND = "host:track-devices";
    private static final String ADB_TRACK_JDWP_COMMAND = "track-jdwp";

    private volatile boolean mQuit = false;
    private final AndroidDebugBridge mServer;
    private DeviceListMonitorTask mDeviceListMonitorTask;
    private final List<Device> mDevices = Lists.newCopyOnWriteArrayList();
    private final DebuggerPorts mDebuggerPorts = new DebuggerPorts(DdmPreferences.getDebugPortBase());
    private final Map<Client, Integer> mClientsToReopen = new HashMap<>();
    private final BlockingQueue<Pair<SocketChannel, Device>> mChannelsToRegister = Queues.newLinkedBlockingQueue();
    private volatile ScheduledExecutorService jdwpTrackExecutor;

    /**
     * Creates a new {@link DeviceMonitor} object and links it to the running {@link AndroidDebugBridge} object.
     *
     * @param server the running {@link AndroidDebugBridge}.
     */
    DeviceMonitor(@NonNull AndroidDebugBridge server) {
        mServer = server;

    }

    /**
     * Starts the monitoring.
     */
    void start() {
        mDeviceListMonitorTask = new DeviceListMonitorTask(mServer, new DeviceListUpdateListener());
    }

    /**
     * Stops the monitoring.
     */
    void stop() {
        mQuit = true;
        if (mDeviceListMonitorTask != null) {
            mDeviceListMonitorTask.stop();
            mDeviceListMonitorTask = null;
        }
    }

    private synchronized void createJdwpTrackerIfNecessary() {
        if (Objects.isNull(jdwpTrackExecutor)) {
            log.debug("createJdwpTracker");
            jdwpTrackExecutor = new ScheduledThreadPoolExecutor(1, new DefaultThreadFactory("JdwpTracker"));
            jdwpTrackExecutor.scheduleAtFixedRate(this::deviceClientMonitorLoop, 0, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * Returns whether the monitor is currently connected to the debug bridge server.
     */
    boolean isMonitoring() {
        return mDeviceListMonitorTask != null && mDeviceListMonitorTask.isMonitoring();
    }

    int getConnectionAttemptCount() {
        return mDeviceListMonitorTask == null ? 0
            : mDeviceListMonitorTask.getConnectionAttemptCount();
    }

    int getRestartAttemptCount() {
        return mDeviceListMonitorTask == null ? 0 : mDeviceListMonitorTask.getRestartAttemptCount();
    }

    boolean hasInitialDeviceList() {
        return mDeviceListMonitorTask != null && mDeviceListMonitorTask.hasInitialDeviceList();
    }

    /**
     * Returns the devices.
     */
    @NonNull
    Device[] getDevices() {
        // Since this is a copy of write array list, we don't want to do a compound operation
        // (toArray with an appropriate size) without locking, so we just let the container provide
        // an appropriately sized array
        return mDevices.toArray(new Device[0]);
    }

    @NonNull
    AndroidDebugBridge getServer() {
        return mServer;
    }

    @Override
    public void trackClientToDropAndReopen(@NonNull Client client, int port) {
        synchronized (mClientsToReopen) {
            log.debug("Adding " + client + " to list of client to reopen (" + port + ").");
            mClientsToReopen.putIfAbsent(client, port);
        }
    }

    /**
     * Attempts to connect to the debug bridge server.
     *
     * @return a connect socket if success, null otherwise
     */
    @Nullable
    private static AdbConnection openAdbConnection(String serialNumber) {
        try {
            return connect(AndroidDebugBridge.getSocketAddress(), serialNumber);
        } catch (IOException e) {
            log.error("Unable to open connection to: " + AndroidDebugBridge.getSocketAddress() + ", due to: " + e);
            return null;
        }
    }

    /**
     * Updates the device list with the new items received from the monitoring service.
     */
    private void updateDevices(@NonNull List<Device> newList) {
        DeviceListComparisonResult result = DeviceListComparisonResult.compare(mDevices, newList);
        for (IDevice device : result.removed) {
            removeDevice((Device) device);
            AndroidDebugBridge.deviceDisconnected(device);
        }

        List<Device> newlyOnline = Lists.newArrayListWithExpectedSize(mDevices.size());

        for (Map.Entry<IDevice, IDevice.DeviceState> entry : result.updated.entrySet()) {
            Device device = (Device) entry.getKey();
            device.setState(entry.getValue());
            device.update(Device.CHANGE_STATE);

            if (device.isOnline()) {
                newlyOnline.add(device);
            }
        }

        for (IDevice device : result.added) {
            mDevices.add((Device) device);
            AndroidDebugBridge.deviceConnected(device);
            if (device.isOnline()) {
                newlyOnline.add((Device) device);
            }
        }

        if (AndroidDebugBridge.getClientSupport()) {
            for (Device device : newlyOnline) {
                if (!startMonitoringDevice(device)) {
                    log.error("Failed to start monitoring " + device.getSerialNumber());
                } else {
                    log.debug("Start monitor device {}", device.getSerialNumber());
                }
            }
        }

        for (Device device : newlyOnline) {
            queryAvdName(device);

            // Initiate a property fetch so that future requests can be served out of this cache.
            // This is necessary for backwards compatibility
            device.getSystemProperty(IDevice.PROP_BUILD_API_LEVEL);
        }
    }

    private void removeDevice(@NonNull Device device) {
        device.setState(IDevice.DeviceState.DISCONNECTED);
        device.clearClientList();
        mDevices.remove(device);
        AdbConnection channel = device.getClientMonitoringSocket();
        if (channel != null) {
            channel.close();
        }
    }

    private static void queryAvdName(@NonNull Device device) {
        if (!device.isEmulator()) {
            return;
        }

        EmulatorConsole console = EmulatorConsole.getConsole(device);
        if (console != null) {
            device.setAvdName(console.getAvdName());
            console.close();
        }
    }

    /**
     * Starts a monitoring service for a device.
     *
     * @param device the device to monitor.
     * @return true if success.
     */
    private boolean startMonitoringDevice(@NonNull Device device) {
        createJdwpTrackerIfNecessary();
        AdbConnection socketChannel = openAdbConnection(device.getSerialNumber());
        if (socketChannel != null) {
            try {
                boolean result = sendDeviceMonitoringRequest(socketChannel, device);
                if (result) {
                    device.setClientMonitoringSocket(socketChannel);
                    return true;
                } else {
                    socketChannel.close();
                }
            } catch (TimeoutException e) {
                socketChannel.close();
                log.debug("Connection Failure when starting to monitor device '" + device + "' : timeout");
            } catch (AdbCommandRejectedException e) {
                socketChannel.close();
                log.debug("Adb refused to start monitoring device '" + device + "' : " + e.getMessage());
            }
        }
        return false;
    }

    private void deviceClientMonitorLoop() {
        if (mQuit) {
            return;
        }
        try {
            synchronized (mClientsToReopen) {
                if (!mClientsToReopen.isEmpty()) {
                    Set<Client> clients = mClientsToReopen.keySet();
                    MonitorThread monitorThread = MonitorThread.getInstance();
                    for (Client client : clients) {
                        Device device = client.getDeviceImpl();
                        int pid = client.getClientData().getPid();
                        monitorThread.dropClient(client, false);
                        // This is kinda bad, but if we don't wait a bit, the client
                        // will never answer the second handshake!
                        Thread.sleep(1000);
                        int port = mClientsToReopen.get(client);
                        if (port == IDebugPortProvider.NO_STATIC_PORT) {
                            port = getNextDebuggerPort();
                        }
                        Log.d("DeviceMonitor", "Reopening " + client);
                        openClient(device, pid, port, monitorThread);
                        device.update(Device.CHANGE_CLIENT_LIST);
                    }
                    mClientsToReopen.clear();
                }
            }
        } catch (InterruptedException e) {
            log.error("Interrupted, {}", e);
        }
    }

    private boolean sendDeviceMonitoringRequest(@NonNull AdbConnection socket, @NonNull Device device)
        throws TimeoutException, AdbCommandRejectedException {
        try {
            AdbHelper.setDevice(socket, device);
            try {
                socket.sendAndWaitSuccess(ADB_TRACK_JDWP_COMMAND, DdmPreferences.getTimeOut(), TimeUnit.MILLISECONDS,
                    new JdwpTrackHandler(device));
                return true;
            } catch (Exception e) {
                log.error("adb refused request: {}", e);
                return false;
            }
        } catch (TimeoutException e) {
            Log.e("DeviceMonitor", "Sending jdwp tracking request timed out!");
            throw e;
        }
    }

    /** Opens and creates a new client. */
    private static void openClient(@NonNull Device device, int pid, int port,
        @NonNull MonitorThread monitorThread) {

        SocketChannel clientSocket;
        try {
            clientSocket = AdbHelper.createPassThroughConnection(
                AndroidDebugBridge.getSocketAddress(), device, pid);

            // required for Selector
            clientSocket.configureBlocking(false);
        } catch (UnknownHostException uhe) {
            Log.d("DeviceMonitor", "Unknown Jdwp pid: " + pid);
            return;
        } catch (TimeoutException e) {
            Log.w("DeviceMonitor",
                "Failed to connect to client '" + pid + "': timeout");
            return;
        } catch (AdbCommandRejectedException e) {
            Log.w("DeviceMonitor",
                "Adb rejected connection to client '" + pid + "': " + e.getMessage());
            return;

        } catch (IOException ioe) {
            Log.w("DeviceMonitor",
                "Failed to connect to client '" + pid + "': " + ioe.getMessage());
            return;
        }

        createClient(device, pid, clientSocket, port, monitorThread);
    }

    /** Creates a client and register it to the monitor thread */
    private static void createClient(@NonNull Device device, int pid, @NonNull SocketChannel socket,
        int debuggerPort, @NonNull MonitorThread monitorThread) {

        /*
         * Successfully connected to something. Create a Client object, add
         * it to the list, and initiate the JDWP handshake.
         */

        Client client = new Client(device, socket, pid);

        if (client.sendHandshake()) {
            try {
                if (AndroidDebugBridge.getClientSupport()) {
                    client.listenForDebugger(debuggerPort);
                    String msg = String.format(Locale.US, "Opening a debugger listener at port %1$d for client with pid %2$d",
                        debuggerPort, pid);
                    Log.i("ddms", msg);
                }
            } catch (IOException ioe) {
                client.getClientData().setDebuggerConnectionStatus(DebuggerStatus.ERROR);
                Log.e("ddms", "Can't bind to local " + debuggerPort + " for debugger");
                // oh well
            }

            client.requestAllocationStatus();
        } else {
            Log.e("ddms", "Handshake with " + client + " failed!");
            /*
             * The handshake send failed. We could remove it now, but if the
             * failure is "permanent" we'll just keep banging on it and
             * getting the same result. Keep it in the list with its "error"
             * state so we don't try to reopen it.
             */
        }

        if (client.isValid()) {
            device.addClient(client);
            monitorThread.addClient(client);
        }
    }

    private int getNextDebuggerPort() {
        return mDebuggerPorts.next();
    }

    @Override
    public void trackDisconnectedClient(@NonNull Client client) {
        mDebuggerPorts.free(client.getDebuggerListenPort());
    }

    /**
     * Reads the length of the next message from a socket.
     *
     * @param socket The {@link SocketChannel} to read from.
     * @return the length, or 0 (zero) if no data is available from the socket.
     * @throws IOException if the connection failed.
     */
    private static int readLength(@NonNull SocketChannel socket, @NonNull byte[] buffer)
        throws IOException {
        String msg = read(socket, buffer);

        try {
            return Integer.parseInt(msg, 16);
        } catch (NumberFormatException nfe) {
            // we'll throw an exception below.
        }

        // we receive something we can't read. It's better to reset the connection at this point.
        throw new IOException("Unable to read length");
    }

    /**
     * Fills a buffer by reading data from a socket.
     *
     * @return the content of the buffer as a string, or null if it failed to convert the buffer.
     * @throws IOException if there was not enough data to fill the buffer
     */
    @NonNull
    private static String read(@NonNull SocketChannel socket, @NonNull byte[] buffer)
        throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(buffer, 0, buffer.length);

        while (buf.position() != buf.limit()) {
            int count;

            count = socket.read(buf);
            if (count < 0) {
                throw new IOException("EOF");
            }
        }

        return new String(buffer, 0, buf.position(), AdbHelper.DEFAULT_CHARSET);
    }

    private class DeviceListUpdateListener implements DeviceListMonitorTask.UpdateListener {
        @Override
        public void connectionError(@NonNull Exception e) {
            for (Device device : mDevices) {
                removeDevice(device);
                AndroidDebugBridge.deviceDisconnected(device);
            }
        }

        @Override
        public void deviceListUpdate(@NonNull Map<String, IDevice.DeviceState> devices) {
            List<Device> l = Lists.newArrayListWithExpectedSize(devices.size());
            for (Map.Entry<String, IDevice.DeviceState> entry : devices.entrySet()) {
                l.add(new Device(DeviceMonitor.this, entry.getKey(), entry.getValue()));
            }
            // now merge the new devices with the old ones.
            updateDevices(l);
        }
    }

    @SuppressWarnings("WeakerAccess")
    @VisibleForTesting
    static class DeviceListComparisonResult {
        @NonNull public final Map<IDevice, IDevice.DeviceState> updated;
        @NonNull public final List<IDevice> added;
        @NonNull public final List<IDevice> removed;

        private DeviceListComparisonResult(@NonNull Map<IDevice, IDevice.DeviceState> updated,
            @NonNull List<IDevice> added,
            @NonNull List<IDevice> removed) {
            this.updated = updated;
            this.added = added;
            this.removed = removed;
        }

        @NonNull
        public static DeviceListComparisonResult compare(@NonNull List<? extends IDevice> previous,
            @NonNull List<? extends IDevice> current) {
            current = Lists.newArrayList(current);

            final Map<IDevice, IDevice.DeviceState> updated = Maps.newHashMapWithExpectedSize(current.size());
            final List<IDevice> added = Lists.newArrayListWithExpectedSize(1);
            final List<IDevice> removed = Lists.newArrayListWithExpectedSize(1);

            for (IDevice device : previous) {
                IDevice currentDevice = find(current, device);
                if (currentDevice != null) {
                    if (currentDevice.getState() != device.getState()) {
                        updated.put(device, currentDevice.getState());
                    }
                    current.remove(currentDevice);
                } else {
                    removed.add(device);
                }
            }

            added.addAll(current);

            return new DeviceListComparisonResult(updated, added, removed);
        }

        @Nullable
        private static IDevice find(@NonNull List<? extends IDevice> devices,
            @NonNull IDevice device) {
            for (IDevice d : devices) {
                if (d.getSerialNumber().equals(device.getSerialNumber())) {
                    return d;
                }
            }

            return null;
        }
    }

    @VisibleForTesting
    @ChannelHandler.Sharable
    static class DeviceListMonitorTask extends ChannelInboundHandlerAdapter implements Runnable, AdbInputHandler {
        static final int MAX_RECONNECT_TIME = 10;
        private final AndroidDebugBridge mBridge;
        private final UpdateListener mListener;
        private AdbConnection mAdbConnection = null;
        private boolean mMonitoring = false;
        private int mConnectionAttempt = 0;
        private int mRestartAttemptCount = 0;
        private boolean mInitialDeviceListDone = false;
        private volatile boolean mQuit;
        private ScheduledExecutorService scheduledExecutorService;

        private interface UpdateListener {
            void connectionError(@NonNull Exception e);

            void deviceListUpdate(@NonNull Map<String, IDevice.DeviceState> devices);
        }

        DeviceListMonitorTask(@NonNull AndroidDebugBridge bridge, @NonNull UpdateListener listener) {
            mBridge = bridge;
            mListener = listener;
            scheduledExecutorService = new ScheduledThreadPoolExecutor(1,
                new DefaultThreadFactory("DeviceListMonitor"));
            scheduledExecutorService.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
        }

        @Override
        public void run() {
            if (!mQuit) {
                if (mAdbConnection == null) {
                    log.debug("Opening adb connection");
                    mAdbConnection = openAdbConnection(null);
                    if (mAdbConnection == null) {
                        mConnectionAttempt++;
                        log.error("Connection attempts: " + mConnectionAttempt);
                        if (mConnectionAttempt > MAX_RECONNECT_TIME) {
                            if (!mBridge.startAdb()) {
                                mRestartAttemptCount++;
                                log.error("adb restart attempts: " + mRestartAttemptCount);
                            } else {
                                log.info("adb restarted");
                                mRestartAttemptCount = 0;
                            }
                        }
                    } else {
                        log.debug("Connected to adb for device monitoring");
                        mConnectionAttempt = 0;
                        try {
                            mAdbConnection.sendAndWaitSuccess(ADB_TRACK_DEVICES_COMMAND, DdmPreferences.getTimeOut(),
                                TimeUnit.MILLISECONDS, new DeviceMonitorHandler(), this);
                            mMonitoring = true;
                        } catch (Exception e) {
                            handleException(e);
                        }
                    }
                }
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            mListener.deviceListUpdate((Map<String, IDevice.DeviceState>) msg);
            mInitialDeviceListDone = true;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            handleException(new Exception("Disconnect"));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            handleException(cause);
        }

        private void handleException(@NonNull Throwable e) {
            if (!mQuit) {
                if (e instanceof TimeoutException) {
                    log.error("Adb connection Error: timeout");
                } else {
                    log.error("Adb connection Error:" + e.getMessage());
                }
                if (mAdbConnection != null) {
                    mAdbConnection.close();
                    mAdbConnection = null;
                    mMonitoring = false;
                    mListener.connectionError(new Exception("Get a exception", e));
                }
            }
        }

        boolean isMonitoring() {
            return mMonitoring;
        }

        boolean hasInitialDeviceList() {
            return mInitialDeviceListDone;
        }

        int getConnectionAttemptCount() {
            return mConnectionAttempt;
        }

        int getRestartAttemptCount() {
            return mRestartAttemptCount;
        }

        public void stop() {
            mQuit = true;
            scheduledExecutorService.shutdownNow();
            if (mAdbConnection != null) {
                mAdbConnection.close();
            }

        }
    }

    class JdwpTrackHandler extends ByteToMessageDecoder implements AdbInputHandler {

        private static final int JDWP_LENGTH_FIELD_SIZE = 4;
        private final byte[] lengthFieldBuffer = new byte[JDWP_LENGTH_FIELD_SIZE];
        private int length;
        private boolean readLength = true;
        private Device device;

        JdwpTrackHandler(Device device) {
            this.device = device;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (readLength) {
                if (in.readableBytes() >= JDWP_LENGTH_FIELD_SIZE) {
                    in.readBytes(lengthFieldBuffer, 0, JDWP_LENGTH_FIELD_SIZE);
                    length = Integer.parseInt(new String(lengthFieldBuffer), 16);
                    if (length <= 0) {
                        processIncomingJdwpData(new JdwpEvent(device, new HashSet<>()));
                    } else {
                        readLength = false;
                    }
                }
            } else {
                Set<Integer> newPids = new HashSet<>();
                if (in.readableBytes() >= length) {
                    byte[] buffer = new byte[length];
                    in.readBytes(buffer, 0, length);
                    String result = new String(buffer);
                    // split each line in its own list and create an array of integer pid
                    String[] pids = result.split("\n");
                    for (String pid : pids) {
                        try {
                            newPids.add(Integer.valueOf(pid));
                        } catch (NumberFormatException nfe) {
                            // looks like this pid is not really a number. Lets ignore it.
                        }
                    }
                    processIncomingJdwpData(new JdwpEvent(device, newPids));
                    readLength = true;
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.debug("Error reading jdwp list: disconnect");
            reconnectIfNecessary();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("Error reading jdwp list: " + cause.getMessage());
            ctx.close();
            reconnectIfNecessary();
        }

        private void reconnectIfNecessary() {
            // restart the monitoring of that device
            if (mDevices.contains(device) && !mQuit) {
                log.debug("Restarting monitoring service for " + device);
                startMonitoringDevice(device);
            }
        }

        private void processIncomingJdwpData(JdwpEvent jdwpEvent) {
            // This methods reads @length bytes from the @monitorSocket channel.
            // These bytes correspond to the pids of the current set of processes on the device.
            // It takes this set of pids and compares them with the existing set of clients
            // for the device. Clients that correspond to pids that are not alive anymore are
            // dropped, and new clients are created for pids that don't have a corresponding Client.
            // array for the current pids.
            Set<Integer> newPids = jdwpEvent.getNewPids();
            Device device = ((Device) jdwpEvent.getDevice());
            log.debug("processIncomingJdwpData, {}, newPids: {}", device.getSerialNumber(), newPids);
            MonitorThread monitorThread = MonitorThread.getInstance();

            List<Client> clients = device.getClientList();
            Map<Integer, Client> existingClients = new HashMap<>();

            synchronized (clients) {
                for (Client c : clients) {
                    existingClients.put(c.getClientData().getPid(), c);
                }
            }

            Set<Client> clientsToRemove = new HashSet<>();
            for (Integer pid : existingClients.keySet()) {
                if (!newPids.contains(pid)) {
                    clientsToRemove.add(existingClients.get(pid));
                }
            }

            Set<Integer> pidsToAdd = new HashSet<>(newPids);
            pidsToAdd.removeAll(existingClients.keySet());

            monitorThread.dropClients(clientsToRemove, false);

            // at this point whatever pid is left in the list needs to be converted into Clients.
            for (int newPid : pidsToAdd) {
                openClient(device, newPid, getNextDebuggerPort(), monitorThread);
            }

            if (!pidsToAdd.isEmpty() || !clientsToRemove.isEmpty()) {
                AndroidDebugBridge.deviceChanged(device, Device.CHANGE_CLIENT_LIST);
            }
        }
    }
}
