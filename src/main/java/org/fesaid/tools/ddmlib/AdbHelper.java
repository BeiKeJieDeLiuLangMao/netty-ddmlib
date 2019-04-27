package org.fesaid.tools.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.log.LogReceiver;
import org.fesaid.tools.ddmlib.netty.AdbConnection;
import org.fesaid.tools.ddmlib.netty.AdbConnector;
import org.fesaid.tools.ddmlib.netty.AdbNettyConfig;
import org.fesaid.tools.ddmlib.netty.input.AdbFrameHandler;
import org.fesaid.tools.ddmlib.netty.input.AdbStreamInputHandler;
import org.fesaid.tools.ddmlib.netty.input.FullByteBufInputHandler;

import static org.fesaid.tools.ddmlib.AdbHelper.AdbService.SHELL;

/**
 * Helper class to handle requests and connections to adb.
 * <p>{@link AndroidDebugBridge} is the public API to connection to adb, while {@link AdbHelper}
 * does the low level stuff.
 * <p>This currently uses spin-wait non-blocking I/O. A Selector would be more efficient,
 * but seems like overkill for what we're doing here.
 */
@SuppressWarnings({"WeakerAccess"})
@Slf4j public final class AdbHelper {

    // public static final long kOkay = 0x59414b4fL;
    // public static final long kFail = 0x4c494146L;

    /**
     * spin-wait sleep, in ms
     */
    private static final int WAIT_TIME = 5;

    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    static AdbConnector adbConnector;

    /** do not instantiate */
    private AdbHelper() {
    }

    public static void init(AdbNettyConfig config) {
        adbConnector = new AdbConnector(config);
    }

    /**
     * Response from ADB.
     */
    @SuppressWarnings("WeakerAccess")
    static class AdbResponse {
        public AdbResponse() {
            message = "";
        }

        /**
         * first 4 bytes in response were "OKAY"?
         */
        public boolean okay;

        /**
         * diagnostic string if #okay is false
         */
        public String message;
    }

    static AdbConnection connect(InetSocketAddress address, String serialNumber) throws IOException {
        return adbConnector.connect(address, serialNumber);
    }

    /**
     * Creates and connects a new pass-through socket, from the host to a port on the device.
     *
     * @param adbSockAddr adb socket address
     * @param device the device to connect to. Can be null in which case the connection will be to the first available
     * device.
     * @param pid the process pid to connect to.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public static SocketChannel createPassThroughConnection(InetSocketAddress adbSockAddr,
        Device device, int pid) throws TimeoutException, AdbCommandRejectedException, IOException {

        SocketChannel adbChan = SocketChannel.open(adbSockAddr);
        try {
            adbChan.socket().setTcpNoDelay(true);
            adbChan.configureBlocking(false);
            // if the device is not -1, then we first tell adb we're looking to
            // talk to a specific device
            setDevice(adbChan, device);
            byte[] req = createJdwpForwardRequest(pid);
            write(adbChan, req);
            AdbResponse resp = readAdbResponse(adbChan);
            if (!resp.okay) {
                throw new AdbCommandRejectedException(resp.message);
            }
            adbChan.configureBlocking(true);
        } catch (TimeoutException | AdbCommandRejectedException | IOException e) {
            adbChan.close();
            throw e;
        }
        return adbChan;
    }

    /**
     * Creates a port forwarding request to a jdwp process. This returns an array containing "####jwdp:{pid}".
     *
     * @param pid the jdwp process pid on the device.
     */
    private static byte[] createJdwpForwardRequest(int pid) {
        String reqStr = String.format("jdwp:%1$d", pid);
        return formAdbRequest(reqStr);
    }

    /**
     * Create an ASCII string preceded by four hex digits. The opening "####" is the length of the rest of the string,
     * encoded as ASCII hex (case doesn't matter).
     */
    public static byte[] formAdbRequest(String req) {
        String resultStr = String.format("%04X%s", req.length(), req);
        byte[] result = resultStr.getBytes(DEFAULT_CHARSET);
        assert result.length == req.length() + 4;
        return result;
    }

    /**
     * Reads the response from ADB after a command.
     *
     * @param chan The socket channel that is connected to adb.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws IOException in case of I/O error on the connection.
     */
    static AdbResponse readAdbResponse(SocketChannel chan)
        throws TimeoutException, IOException {
        AdbResponse resp = new AdbResponse();
        boolean readDiagString = false;
        byte[] reply = new byte[4];
        read(chan, reply);

        if (isOkay(reply)) {
            resp.okay = true;
        } else {
            // look for a reason after the FAIL
            readDiagString = true;
            resp.okay = false;
        }

        try {
            // not a loop -- use "while" so we can use "break"
            while (readDiagString) {
                // length string is in next 4 bytes
                byte[] lenBuf = new byte[4];
                read(chan, lenBuf);

                String lenStr = replyToString(lenBuf);

                int len;
                try {
                    len = Integer.parseInt(lenStr, 16);
                } catch (NumberFormatException nfe) {
                    Log.w("ddms", "Expected digits, got '" + lenStr + "': "
                        + lenBuf[0] + " " + lenBuf[1] + " " + lenBuf[2] + " "
                        + lenBuf[3]);
                    Log.w("ddms", "reply was " + replyToString(reply));
                    break;
                }

                byte[] msg = new byte[len];
                read(chan, msg);

                resp.message = replyToString(msg);
                Log.v("ddms", "Got reply '" + replyToString(reply) + "', diag='"
                    + resp.message + "'");

                break;
            }
        } catch (Exception e) {
            // ignore those, since it's just reading the diagnose string, the response will
            // contain okay==false anyway.
        }

        return resp;
    }

    /**
     * Retrieve the frame buffer from the device with the given timeout. A timeout of 0 indicates that it will wait
     * forever.
     *
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    static RawImage getFrameBuffer(InetSocketAddress adbSockAddr, Device device, long timeout, TimeUnit unit)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        try (AdbConnection adbConnection = adbConnector.connect(adbSockAddr, device.getSerialNumber())) {
            setDevice(adbConnection, device);
            AdbFrameHandler adbFrameHandler = new AdbFrameHandler();
            adbConnection.sendAndWaitSuccess(
                "framebuffer:",
                DdmPreferences.getTimeOut(),
                TimeUnit.MILLISECONDS,
                adbFrameHandler
            );
            return adbFrameHandler.waitFrameData(timeout, unit);
        }
    }

    /**
     * Executes a shell command on the device and retrieve the output. The output is handed to
     * <var>rcvr</var> as it arrives.
     *
     * @param adbSockAddr the {@link InetSocketAddress} to adb.
     * @param command the shell command to execute
     * @param device the {@link IDevice} on which to execute the command.
     * @param rcvr the {@link IShellOutputReceiver} that will receives the output of the shell command
     * @param maxTimeout max time for the command to return. A value of 0 means no max timeout will be applied.
     * @param maxTimeToOutputResponse max time between command output. If more time passes between command output, the
     * method will throw {@link ShellCommandUnresponsiveException}. A value of 0 means the method will wait forever for
     * command output and never throw.
     * @param maxTimeUnits Units for non-zero {@code maxTimeout} and {@code maxTimeToOutputResponse} values.
     * @throws TimeoutException in case of timeout on the connection when sending the command.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send any output for a period longer
     * than <var>maxTimeToOutputResponse</var>.
     * @throws IOException in case of I/O error on the connection.
     * @see DdmPreferences#getTimeOut()
     */
    static void executeRemoteCommand(
        InetSocketAddress adbSockAddr,
        String command,
        IDevice device,
        IShellOutputReceiver rcvr,
        long maxTimeout,
        long maxTimeToOutputResponse,
        TimeUnit maxTimeUnits)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException {
        executeRemoteCommand(
            adbSockAddr,
            SHELL,
            command,
            device,
            rcvr,
            maxTimeout,
            maxTimeToOutputResponse,
            maxTimeUnits,
            null);
    }

    /**
     * Executes a shell command on the device and retrieve the output. The output is handed to <var>rcvr</var> as it
     * arrives.
     *
     * @param adbSockAddr the {@link InetSocketAddress} to adb.
     * @param command the shell command to execute
     * @param device the {@link IDevice} on which to execute the command.
     * @param rcvr the {@link IShellOutputReceiver} that will receives the output of the shell command
     * @param maxTimeToOutputResponse max time between command output. If more time passes between command output, the
     * method will throw {@link ShellCommandUnresponsiveException}. A value of 0 means the method will wait forever for
     * command output and never throw.
     * @param maxTimeUnits Units for non-zero {@code maxTimeToOutputResponse} values.
     * @throws TimeoutException in case of timeout on the connection when sending the command.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send any output for a period longer
     * than <var>maxTimeToOutputResponse</var>.
     * @throws IOException in case of I/O error on the connection.
     * @see DdmPreferences#getTimeOut()
     */
    @SuppressWarnings("SameParameterValue")
    static void executeRemoteCommand(InetSocketAddress adbSockAddr,
        String command, IDevice device, IShellOutputReceiver rcvr, long maxTimeToOutputResponse, TimeUnit maxTimeUnits)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        executeRemoteCommand(adbSockAddr, SHELL, command, device, rcvr, maxTimeToOutputResponse, maxTimeUnits,
            null);
    }

    /**
     * Identify which adb service the command should target.
     */
    public enum AdbService {
        /**
         * the shell service
         */
        SHELL,

        /**
         * The exec service.
         */
        EXEC
    }

    /**
     * Executes a remote command on the device and retrieve the output. The output is handed to
     * <var>rcvr</var> as it arrives. The command is execute by the remote service identified by the
     * adbService parameter.
     *
     * @param adbSockAddr the {@link InetSocketAddress} to adb.
     * @param adbService the {@link AdbService} to use to run the command.
     * @param command the shell command to execute
     * @param device the {@link IDevice} on which to execute the command.
     * @param rcvr the {@link IShellOutputReceiver} that will receives the output of the shell command
     * @param maxTimeout max timeout for the full command to execute. A value of 0 means no timeout.
     * @param maxTimeToOutputResponse max time between command output. If more time passes between command output, the
     * method will throw {@link ShellCommandUnresponsiveException}. A value of 0 means the method will wait forever for
     * command output and never throw.
     * @param maxTimeUnits Units for non-zero {@code maxTimeout} and {@code maxTimeToOutputResponse} values.
     * @param is a optional {@link InputStream} to be streamed up after invoking the command and before retrieving the
     * response.
     * @throws TimeoutException in case of timeout on the connection when sending the command.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send any output for a period longer
     * than <var>maxTimeToOutputResponse</var>.
     * @throws IOException in case of I/O error on the connection.
     * @see DdmPreferences#getTimeOut()
     */
    static void executeRemoteCommand(InetSocketAddress adbSockAddr, AdbService adbService, String command,
        IDevice device, IShellOutputReceiver rcvr, long maxTimeout, long maxTimeToOutputResponse, TimeUnit maxTimeUnits,
        @Nullable InputStream is) throws TimeoutException, AdbCommandRejectedException,
        ShellCommandUnresponsiveException, IOException {
        log.debug("Adb execute command: " + command);
        try (AdbConnection adbConnection = adbConnector.connect(adbSockAddr, device.getSerialNumber())) {
            // if the device is not -1, then we first tell adb we're looking to
            // talk to a specific device
            setDevice(adbConnection, device);
            AdbStreamInputHandler customRespondHandler = new AdbStreamInputHandler(rcvr);
            adbConnection.sendAndWaitSuccess(
                adbService.name().toLowerCase() + ":" + command,
                DdmPreferences.getTimeOut(),
                TimeUnit.MILLISECONDS, customRespondHandler);
            // stream the input file if present.
            if (!Objects.isNull(is)) {
                adbConnection.send(is);
            }
            customRespondHandler.waitResponseBegin(maxTimeToOutputResponse, maxTimeUnits);
            customRespondHandler.waitFinish(maxTimeout, maxTimeUnits);
        }
    }

    static ByteBuf executeRemoteCommand(InetSocketAddress address, String command, Device device, long timeout,
        TimeUnit timeUnit) throws IOException, TimeoutException, AdbCommandRejectedException {
        log.debug("Adb execute command: " + command);
        try (AdbConnection adbConnection = adbConnector.connect(address, device.getSerialNumber())) {
            // if the device is not -1, then we first tell adb we're looking to
            // talk to a specific device
            setDevice(adbConnection, device);
            FullByteBufInputHandler customRespondHandler = new FullByteBufInputHandler();
            adbConnection.sendAndWaitSuccess(
                SHELL.name().toLowerCase() + ":" + command,
                DdmPreferences.getTimeOut(),
                TimeUnit.MILLISECONDS, customRespondHandler);
            return customRespondHandler.waitEnd(timeout, timeUnit);
        }
    }

    /**
     * Executes a remote command on the device and retrieve the output. The output is handed to
     * <var>rcvr</var> as it arrives. The command is execute by the remote service identified by the
     * adbService parameter.
     *
     * @param adbSockAddr the {@link InetSocketAddress} to adb.
     * @param adbService the {@link AdbService} to use to run the command.
     * @param command the shell command to execute
     * @param device the {@link IDevice} on which to execute the command.
     * @param rcvr the {@link IShellOutputReceiver} that will receives the output of the shell command
     * @param maxTimeToOutputResponse max time between command output. If more time passes between command output, the
     * method will throw {@link ShellCommandUnresponsiveException}. A value of 0 means the method will wait forever for
     * command output and never throw.
     * @param maxTimeUnits Units for non-zero {@code maxTimeToOutputResponse} values.
     * @param is a optional {@link InputStream} to be streamed up after invoking the command and before retrieving the
     * response.
     * @throws TimeoutException in case of timeout on the connection when sending the command.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send any output for a period longer
     * than <var>maxTimeToOutputResponse</var>.
     * @throws IOException in case of I/O error on the connection.
     * @see DdmPreferences#getTimeOut()
     */
    static void executeRemoteCommand(
        InetSocketAddress adbSockAddr,
        AdbService adbService,
        String command,
        IDevice device,
        IShellOutputReceiver rcvr,
        long maxTimeToOutputResponse,
        TimeUnit maxTimeUnits,
        @Nullable InputStream is)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
        IOException {
        executeRemoteCommand(
            adbSockAddr,
            adbService,
            command,
            device,
            rcvr,
            0L,
            maxTimeToOutputResponse,
            maxTimeUnits,
            is);
    }

    /**
     * Runs the Event log service on the {@link Device}, and provides its output to the {@link LogReceiver}.
     * <p>This call is blocking until {@link LogReceiver#isCancelled()} returns true.
     *
     * @param adbSockAddr the socket address to connect to adb
     * @param device the Device on which to run the service
     * @param rcvr the {@link LogReceiver} to receive the log output
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public static void runEventLogService(InetSocketAddress adbSockAddr, Device device,
        LogReceiver rcvr) throws TimeoutException, AdbCommandRejectedException, IOException {
        runLogService(adbSockAddr, device, "events", rcvr);
    }

    /**
     * Runs a log service on the {@link Device}, and provides its output to the {@link LogReceiver}.
     * <p>This call is blocking until {@link LogReceiver#isCancelled()} returns true.
     *
     * @param adbSockAddr the socket address to connect to adb
     * @param device the Device on which to run the service
     * @param logName the name of the log file to output
     * @param rcvr the {@link LogReceiver} to receive the log output
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public static void runLogService(InetSocketAddress adbSockAddr, Device device, String logName,
        LogReceiver rcvr) throws TimeoutException, AdbCommandRejectedException, IOException {
        try (AdbConnection adbConnection = adbConnector.connect(adbSockAddr, device.getSerialNumber())) {
            setDevice(adbConnection, device);
            AdbStreamInputHandler customHandler = new AdbStreamInputHandler(rcvr);
            adbConnection.sendAndWaitSuccess(
                "log:" + logName,
                DdmPreferences.getTimeOut(),
                TimeUnit.MILLISECONDS,
                customHandler
            );
            customHandler.waitFinish(0, null);
        }
    }

    /**
     * Creates a port forwarding between a local and a remote port.
     *
     * @param adbSockAddr the socket address to connect to adb
     * @param device the device on which to do the port forwarding
     * @param localPortSpec specification of the local port to forward, should be of format tcp:<port number>
     * @param remotePortSpec specification of the remote port to forward to, one of: tcp:<port> localabstract:<unix
     * domain socket name> localreserved:<unix domain socket name> localfilesystem:<unix domain socket name>
     * dev:<character device name> jdwp:<process pid> (remote only)
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public static void createForward(InetSocketAddress adbSockAddr, Device device,
        String localPortSpec, String remotePortSpec)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        try (AdbConnection adbConnection = adbConnector.connect(adbSockAddr, device.getSerialNumber())) {
            adbConnection.sendAndWaitSuccess(
                String.format("host-serial:%1$s:forward:%2$s;%3$s", device.getSerialNumber(), localPortSpec, remotePortSpec),
                DdmPreferences.getTimeOut(),
                TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Remove a port forwarding between a local and a remote port.
     *
     * @param adbSockAddr the socket address to connect to adb
     * @param device the device on which to remove the port forwarding
     * @param localPortSpec specification of the local port that was forwarded, should be of format tcp:<port number>
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public static void removeForward(InetSocketAddress adbSockAddr, Device device,
        String localPortSpec)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        try (AdbConnection adbConnection = adbConnector.connect(adbSockAddr, device.getSerialNumber())) {
            adbConnection.sendAndWaitSuccess(
                String.format("host-serial:%1$s:killforward:%2$s", device.getSerialNumber(), localPortSpec),
                DdmPreferences.getTimeOut(),
                TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Checks to see if the first four bytes in "reply" are OKAY.
     */
    static boolean isOkay(byte[] reply) {
        return reply[0] == (byte) 'O' && reply[1] == (byte) 'K'
            && reply[2] == (byte) 'A' && reply[3] == (byte) 'Y';
    }

    /**
     * Converts an ADB reply to a string.
     */
    static String replyToString(byte[] reply) {
        return new String(reply, DEFAULT_CHARSET);
    }

    /**
     * Reads from the socket until the array is filled, or no more data is coming (because the socket closed or the
     * timeout expired).
     * <p>This uses the default time out value.
     *
     * @param chan the opened socket to read from. It must be in non-blocking mode for timeouts to work
     * @param data the buffer to store the read data into.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws IOException in case of I/O error on the connection.
     */
    static void read(SocketChannel chan, byte[] data) throws TimeoutException, IOException {
        read(chan, data, -1, DdmPreferences.getTimeOut());
    }

    /**
     * Reads from the socket until the array is filled, the optional length is reached, or no more data is coming
     * (because the socket closed or the timeout expired). After "timeout" milliseconds since the previous successful
     * read, this will return whether or not new data has been found.
     *
     * @param chan the opened socket to read from. It must be in non-blocking mode for timeouts to work
     * @param data the buffer to store the read data into.
     * @param length the length to read or -1 to fill the data buffer completely
     * @param timeout The timeout value in ms. A timeout of zero means "wait forever".
     */
    static void read(SocketChannel chan, byte[] data, int length, long timeout) throws TimeoutException, IOException {
        ByteBuffer buf = ByteBuffer.wrap(data, 0, length != -1 ? length : data.length);
        int numWaits = 0;

        while (buf.position() != buf.limit()) {
            int count;

            count = chan.read(buf);
            if (count < 0) {
                Log.d("ddms", "read: channel EOF");
                throw new IOException("EOF");
            } else if (count == 0) {
                // TODO: need more accurate timeout?
                if (timeout != 0 && numWaits * WAIT_TIME > timeout) {
                    Log.d("ddms", "read: timeout");
                    throw new TimeoutException();
                }
                try {
                    // non-blocking spin
                    Thread.sleep(WAIT_TIME);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // Throw a timeout exception in place of interrupted exception to avoid API changes.
                    throw new TimeoutException("Read interrupted with immediate timeout via interruption.");
                }
                numWaits++;
            } else {
                numWaits = 0;
            }
        }
    }

    /**
     * Write until all data in "data" is written or the connection fails or times out.
     * <p>This uses the default time out value.
     *
     * @param chan the opened socket to write to.
     * @param data the buffer to send.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws IOException in case of I/O error on the connection.
     */
    static void write(SocketChannel chan, byte[] data) throws TimeoutException, IOException {
        write(chan, data, -1, DdmPreferences.getTimeOut());
    }

    /**
     * Write until all data in "data" is written, the optional length is reached, the timeout expires, or the connection
     * fails. Returns "true" if all data was written.
     *
     * @param chan the opened socket to write to.
     * @param data the buffer to send.
     * @param length the length to write or -1 to send the whole buffer.
     * @param timeout The timeout value. A timeout of zero means "wait forever".
     * @throws TimeoutException in case of timeout on the connection.
     * @throws IOException in case of I/O error on the connection.
     */
    static void write(SocketChannel chan, byte[] data, int length, int timeout) throws TimeoutException, IOException {
        ByteBuffer buf = ByteBuffer.wrap(data, 0, length != -1 ? length : data.length);
        int numWaits = 0;
        while (buf.position() != buf.limit()) {
            int count;
            count = chan.write(buf);
            if (count < 0) {
                Log.d("ddms", "write: channel EOF");
                throw new IOException("channel EOF");
            } else if (count == 0) {
                // TODO: need more accurate timeout?
                if (timeout != 0 && numWaits * WAIT_TIME > timeout) {
                    Log.d("ddms", "write: timeout");
                    throw new TimeoutException();
                }
                try {
                    // non-blocking spin
                    Thread.sleep(WAIT_TIME);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // Throw a timeout exception in place of interrupted exception to avoid API changes.
                    throw new TimeoutException("Write interrupted with immediate timeout via interruption.");
                }
                numWaits++;
            } else {
                numWaits = 0;
            }
        }
    }

    /**
     * tells adb to talk to a specific device
     *
     * @param adbChan the socket connection to adb
     * @param device The device to talk to.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    @Deprecated
    static void setDevice(SocketChannel adbChan, IDevice device)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        // if the device is not -1, then we first tell adb we're looking to talk
        // to a specific device
        if (device != null) {
            String msg = "host:transport:" + device.getSerialNumber();
            byte[] deviceQuery = formAdbRequest(msg);
            write(adbChan, deviceQuery);
            AdbResponse resp = readAdbResponse(adbChan);
            if (!resp.okay) {
                throw new AdbCommandRejectedException(resp.message, true);
            }
        }
    }

    /**
     * tells adb to talk to a specific device
     *
     * @param adbConnection the socket connection to adb
     * @param device The device to talk to.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     */
    static void setDevice(AdbConnection adbConnection, IDevice device)
        throws TimeoutException, AdbCommandRejectedException {
        // if the device is not -1, then we first tell adb we're looking to talk
        // to a specific device
        if (device != null) {
            adbConnection.sendAndWaitSuccess(
                "host:transport:" + device.getSerialNumber(),
                DdmPreferences.getTimeOut(),
                TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Reboot the device.
     *
     * @param into what to reboot into (recovery, bootloader).  Or null to just reboot.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public static void reboot(String into, InetSocketAddress adbSockAddr, Device device)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        try (AdbConnection adbConnection = adbConnector.connect(adbSockAddr, device.getSerialNumber())) {
            setDevice(adbConnection, device);
            String message;
            if (into == null) {
                message = "reboot:";
            } else {
                message = "reboot:" + into;
            }
            adbConnection.send(message, DdmPreferences.getTimeOut(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Ask the adb demon to become root on the device. This may silently fail, and can only succeed on developer builds.
     * See "adb root" for more information. If you need to know if succeeded, you can check the result of
     * executeRemoteCommand on 'echo \$USER_ID', if it is 0 then adbd is running as root.
     *
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    public static void root(@NonNull InetSocketAddress adbSockAddr, @NonNull Device device)
        throws TimeoutException, AdbCommandRejectedException, IOException {
        try (AdbConnection adbConnection = adbConnector.connect(adbSockAddr, device.getSerialNumber())) {
            setDevice(adbConnection, device);
            adbConnection.sendAndWaitSuccess("root:", DdmPreferences.getTimeOut(), TimeUnit.MILLISECONDS);
        }
    }
}
