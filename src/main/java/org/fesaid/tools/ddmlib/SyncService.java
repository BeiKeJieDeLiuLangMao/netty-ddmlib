package org.fesaid.tools.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.FileListingService.FileEntry;
import org.fesaid.tools.ddmlib.SyncException.SyncError;
import org.fesaid.tools.ddmlib.netty.AdbConnection;
import org.fesaid.tools.ddmlib.netty.input.PullFileHandler;
import org.fesaid.tools.ddmlib.netty.input.PushFileHandler;
import org.fesaid.tools.ddmlib.netty.input.SameFileCheckHandler;
import org.fesaid.tools.ddmlib.netty.input.StatFileHandler;
import org.fesaid.tools.ddmlib.utils.ArrayHelper;
import org.fesaid.tools.ddmlib.utils.FilePermissionUtil;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.fesaid.tools.ddmlib.AdbHelper.connect;
import static org.fesaid.tools.ddmlib.AdbHelper.setDevice;
import static org.fesaid.tools.ddmlib.DdmPreferences.getTimeOut;

/**
 * Sync service class to push/pull to/from devices/emulators, through the debug bridge.
 * <p>
 * To get a {@link SyncService} object, use {@link Device#getSyncService()}.
 *
 * @author AOSP
 */
@SuppressWarnings({"WeakerAccess", "unused"})
@Slf4j
public class SyncService implements Closeable {
    public static final int HEADER_LENGTH = 8;
    public static final byte[] ID_OKAY = {'O', 'K', 'A', 'Y'};
    private static final byte[] ID_FAIL = {'F', 'A', 'I', 'L'};
    private static final byte[] ID_STAT = {'S', 'T', 'A', 'T'};
    private static final byte[] ID_RECV = {'R', 'E', 'C', 'V'};
    private static final byte[] ID_DATA = {'D', 'A', 'T', 'A'};
    private static final byte[] ID_DONE = {'D', 'O', 'N', 'E'};
    private static final byte[] ID_SEND = {'S', 'E', 'N', 'D'};

    private static final NullSyncProgressMonitor S_NULL_SYNC_PROGRESS_MONITOR = new NullSyncProgressMonitor();

    /**
     * type: symbolic link
     */
    private static final int S_ISOCK = 0xC000;
    /**
     * type: symbolic link
     */
    private static final int S_IFLNK = 0xA000;
    /**
     * type: regular file
     */
    private static final int S_IFREG = 0x8000;
    /**
     * type: block device
     */
    private static final int S_IFBLK = 0x6000;
    /**
     * type: directory
     */
    private static final int S_IFDIR = 0x4000;
    /**
     * type: character device
     */
    private static final int S_IFCHR = 0x2000;
    /**
     * type: fifo
     */
    private static final int S_IFIFO = 0x1000;

    public static final int SYNC_DATA_MAX = 64 * 1024;
    private static final int REMOTE_PATH_MAX_LENGTH = 1024;


    /**
     * Classes which implement this interface provide methods that deal with displaying transfer progress.
     */
    public interface ISyncProgressMonitor {
        /**
         * Sent when the transfer starts
         *
         * @param totalWork the total amount of work.
         */
        void start(int totalWork);

        /**
         * Sent when the transfer is finished or interrupted.
         */
        void stop();

        /**
         * Sent to query for possible cancellation.
         *
         * @return true if the transfer should be stopped.
         */
        boolean isCanceled();

        /**
         * Sent when a sub task is started.
         *
         * @param name the name of the sub task.
         */
        void startSubTask(String name);

        /**
         * Sent when some progress have been made.
         *
         * @param work the amount of work done.
         */
        void advance(int work);
    }

    @SuppressWarnings({"unused"})
    public static class FileStat {
        private final int myMode;
        private final int mySize;
        private final Date myLastModified;

        public FileStat(int mode, int size, int lastModifiedSecs) {
            myMode = mode;
            mySize = size;
            myLastModified = new Date((long) (lastModifiedSecs) * 1000);
        }

        public int getMode() {
            return myMode;
        }

        public int getSize() {
            return mySize;
        }

        public Date getLastModified() {
            return myLastModified;
        }
    }

    /**
     * A Sync progress monitor that does nothing
     */
    private static class NullSyncProgressMonitor implements ISyncProgressMonitor {
        @Override
        public void advance(int work) {
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public void start(int totalWork) {
        }

        @Override
        public void startSubTask(String name) {
        }

        @Override
        public void stop() {
        }
    }

    private InetSocketAddress mAddress;
    private Device mDevice;
    private AdbConnection mChannel;

    /**
     * Buffer used to send data. Allocated when needed and reused afterward.
     */
    private byte[] mBuffer;

    /**
     * Creates a Sync service object.
     *
     * @param address The address to connect to
     * @param device the {@link Device} that the service connects to.
     */
    SyncService(InetSocketAddress address, Device device) {
        mAddress = address;
        mDevice = device;
    }

    /**
     * Opens the sync connection. This must be called before any calls to push[File] / pull[File].
     *
     * @return true if the connection opened, false if adb refuse the connection. This can happen if the {@link Device}
     * is invalid.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException If the connection to adb failed.
     */
    boolean openSync() throws TimeoutException, AdbCommandRejectedException, IOException {
        try {
            mChannel = connect(mAddress, mDevice.getSerialNumber());
            setDevice(mChannel, mDevice);
            mChannel.sendAndWaitSuccess("sync:", getTimeOut(), MILLISECONDS);
            return true;
        } catch (Exception e) {
            if (mChannel != null) {
                mChannel.close();
                mChannel = null;
            }
            throw e;
        }
    }

    /**
     * Closes the connection.
     */
    @Override
    public void close() {
        if (mChannel != null) {
            mChannel.close();
            mChannel = null;
        }
    }

    /**
     * Returns a sync progress monitor that does nothing. This allows background tasks that don't want/need to display
     * ui, to pass a valid {@link ISyncProgressMonitor}.
     * <p>This object can be reused multiple times and can be used by concurrent threads.
     */
    public static ISyncProgressMonitor getNullProgressMonitor() {
        return S_NULL_SYNC_PROGRESS_MONITOR;
    }

    /**
     * Pulls file(s) or folder(s).
     *
     * @param entries the remote item(s) to pull
     * @param localPath The local destination. If the entries count is &gt; 1 or if the unique entry is a folder, this
     * should be a folder.
     * @param monitor The progress monitor. Cannot be null.
     * @throws SyncException SyncException
     * @throws TimeoutException TimeoutException
     * @see FileEntry
     * @see #getNullProgressMonitor()
     */
    public void pull(FileEntry[] entries, String localPath, ISyncProgressMonitor monitor)
        throws SyncException, TimeoutException {

        // first we check the destination is a directory and exists
        File f = new File(localPath);
        if (!f.exists()) {
            throw new SyncException(SyncError.NO_DIR_TARGET);
        }
        if (!f.isDirectory()) {
            throw new SyncException(SyncError.TARGET_IS_FILE);
        }

        // get a FileListingService object
        FileListingService fls = new FileListingService(mDevice);

        // compute the number of file to move
        int total = getTotalRemoteFileSize(entries, fls);

        // start the monitor
        monitor.start(total);

        doPull(entries, localPath, fls, monitor);

        monitor.stop();
    }

    /**
     * Pulls a single file.
     *
     * @param remote the remote file
     * @param localFilename The local destination.
     * @param monitor The progress monitor. Cannot be null.
     * @throws TimeoutException in case of a timeout reading responses from the device.
     * @throws SyncException in case of a sync exception.
     * @see FileEntry
     * @see #getNullProgressMonitor()
     */
    public void pullFile(FileEntry remote, String localFilename, ISyncProgressMonitor monitor)
        throws SyncException, TimeoutException {
        int total = remote.getSizeValue();
        monitor.start(total);
        doPullFile(remote.getFullPath(), localFilename, monitor);
        monitor.stop();
    }

    /**
     * Pulls a single file.
     * <p>Because this method just deals with a String for the remote file instead of a
     * {@link FileEntry}, the size of the file being pulled is unknown and the {@link ISyncProgressMonitor} will not
     * properly show the progress
     *
     * @param remoteFilepath the full path to the remote file
     * @param localFilename The local destination.
     * @param monitor The progress monitor. Cannot be null.
     * @throws TimeoutException in case of a timeout reading responses from the device.
     * @throws SyncException in case of a sync exception.
     * @see #getNullProgressMonitor()
     */
    public void pullFile(String remoteFilepath, String localFilename,
        ISyncProgressMonitor monitor) throws TimeoutException, SyncException {
        FileStat fileStat = statFile(remoteFilepath);
        if (fileStat.getMode() == 0) {
            throw new SyncException(SyncError.NO_REMOTE_OBJECT);
        }
        monitor.start(0);
        //TODO: use the {@link FileListingService} to get the file size.
        doPullFile(remoteFilepath, localFilename, monitor);
        monitor.stop();
    }

    public boolean isSameWithFile(String remoteFilepath, InputStream localStream, ISyncProgressMonitor monitor)
        throws TimeoutException, SyncException {
        FileStat fileStat = statFile(remoteFilepath);
        if (fileStat.getMode() == 0) {
            return false;
        }
        monitor.start(0);
        boolean result = compareFile(remoteFilepath, localStream, monitor);
        monitor.stop();
        return result;
    }

    /**
     * Push several files.
     *
     * @param local An array of loca files to push
     * @param remote the remote {@link FileEntry} representing a directory.
     * @param monitor The progress monitor. Cannot be null.
     * @throws SyncException if file could not be pushed
     * @throws IOException in case of I/O error on the connection.
     * @throws TimeoutException in case of a timeout reading responses from the device.
     */
    public void push(String[] local, FileEntry remote, ISyncProgressMonitor monitor)
        throws SyncException, IOException, TimeoutException {
        if (!remote.isDirectory()) {
            throw new SyncException(SyncError.REMOTE_IS_FILE);
        }

        // make a list of File from the list of String
        ArrayList<File> files = new ArrayList<>();
        for (String path : local) {
            files.add(new File(path));
        }

        // get the total count of the bytes to transfer
        File[] fileArray = files.toArray(new File[0]);
        int total = getTotalLocalFileSize(fileArray);

        monitor.start(total);

        doPush(fileArray, remote.getFullPath(), monitor);

        monitor.stop();
    }

    /**
     * Push a single file.
     *
     * @param local the local filepath.
     * @param remote The remote filepath.
     * @param monitor The progress monitor. Cannot be null.
     * @throws SyncException if file could not be pushed
     * @throws IOException in case of I/O error on the connection.
     * @throws TimeoutException in case of a timeout reading responses from the device.
     */
    public void pushFile(String local, String remote, ISyncProgressMonitor monitor)
        throws SyncException, IOException, TimeoutException {
        File f = new File(local);
        if (!f.exists()) {
            throw new SyncException(SyncError.NO_LOCAL_FILE);
        }
        if (f.isDirectory()) {
            throw new SyncException(SyncError.LOCAL_IS_DIRECTORY);
        }
        monitor.start((int) f.length());
        doPushFile(local, remote, monitor);
        monitor.stop();
    }

    public void pushFile(InputStream local, String remote, ISyncProgressMonitor monitor)
        throws SyncException, IOException, TimeoutException {
        doPushFile(local, remote, monitor, 0644, (int) (System.currentTimeMillis() / 1000));
    }

    public void pushFile(InputStream stream, String remote, ISyncProgressMonitor monitor, int mode)
        throws TimeoutException, SyncException, IOException {
        doPushFile(stream, remote, monitor, mode, (int) (System.currentTimeMillis() / 1000));
    }

    /**
     * compute the recursive file size of all the files in the list. Folder have a weight of 1.
     *
     * @param entries entries
     * @param fls fls
     * @return count
     */
    private int getTotalRemoteFileSize(FileEntry[] entries, FileListingService fls) {
        int count = 0;
        for (FileEntry e : entries) {
            int type = e.getType();
            if (type == FileListingService.TYPE_DIRECTORY) {
                // get the children
                FileEntry[] children = fls.getChildren(e, false, null);
                count += getTotalRemoteFileSize(children, fls) + 1;
            } else if (type == FileListingService.TYPE_FILE) {
                count += e.getSizeValue();
            }
        }

        return count;
    }

    /**
     * compute the recursive file size of all the files in the list. Folder have a weight of 1. This does not check for
     * circular links.
     *
     * @param files files
     * @return count
     */
    private int getTotalLocalFileSize(File[] files) {
        int count = 0;

        for (File f : files) {
            if (f.exists()) {
                if (f.isDirectory()) {
                    return getTotalLocalFileSize(f.listFiles()) + 1;
                } else if (f.isFile()) {
                    count += f.length();
                }
            }
        }

        return count;
    }

    /**
     * Pulls multiple files/folders recursively.
     *
     * @param entries The list of entry to pull
     * @param localPath the localpath to a directory
     * @param fileListingService a FileListingService object to browse through remote directories.
     * @param monitor the progress monitor. Must be started already.
     * @throws SyncException if file could not be pushed
     * @throws TimeoutException in case of a timeout reading responses from the device.
     */
    private void doPull(FileEntry[] entries, String localPath,
        FileListingService fileListingService,
        ISyncProgressMonitor monitor) throws SyncException, TimeoutException {

        for (FileEntry e : entries) {
            // check if we're cancelled
            if (monitor.isCanceled()) {
                throw new SyncException(SyncError.CANCELED);
            }

            // get type (we only pull directory and files for now)
            int type = e.getType();
            if (type == FileListingService.TYPE_DIRECTORY) {
                monitor.startSubTask(e.getFullPath());
                String dest = localPath + File.separator + e.getName();

                // make the directory
                File d = new File(dest);
                d.mkdir();

                // then recursively call the content. Since we did a ls command
                // to get the number of files, we can use the cache
                FileEntry[] children = fileListingService.getChildren(e, true, null);
                doPull(children, dest, fileListingService, monitor);
                monitor.advance(1);
            } else if (type == FileListingService.TYPE_FILE) {
                monitor.startSubTask(e.getFullPath());
                String dest = localPath + File.separator + e.getName();
                doPullFile(e.getFullPath(), dest, monitor);
            }
        }
    }

    /**
     * Pulls a remote file
     *
     * @param remotePath the remote file (length max is 1024)
     * @param localPath the local destination
     * @param monitor the monitor. The monitor must be started already.
     * @throws SyncException if file could not be pushed
     * @throws TimeoutException in case of a timeout reading responses from the device.
     */
    private void doPullFile(String remotePath, String localPath, ISyncProgressMonitor monitor) throws
        SyncException, TimeoutException {

        if (remotePath.getBytes(AdbHelper.DEFAULT_CHARSET).length > REMOTE_PATH_MAX_LENGTH) {
            throw new SyncException(SyncError.REMOTE_PATH_LENGTH);
        }
        // create the full request message
        PullFileHandler pullFileHandler = new PullFileHandler(monitor, new File(localPath));
        mChannel.syncSendAndHandle(createFileReq(ID_RECV, remotePath.getBytes(AdbHelper.DEFAULT_CHARSET)), pullFileHandler,
            getTimeOut(), MILLISECONDS);
        // read the result, in a byte array containing 2 ints (id, size)
        pullFileHandler.waitRespondBegin(getTimeOut(), MILLISECONDS);
        pullFileHandler.waitFinish();
    }

    private boolean compareFile(String remotePath, InputStream localStream, ISyncProgressMonitor monitor) throws
        SyncException, TimeoutException {

        if (remotePath.getBytes(AdbHelper.DEFAULT_CHARSET).length > REMOTE_PATH_MAX_LENGTH) {
            throw new SyncException(SyncError.REMOTE_PATH_LENGTH);
        }
        // create the full request message
        SameFileCheckHandler sameFileCheckHandler = new SameFileCheckHandler(monitor, localStream);
        mChannel.syncSendAndHandle(createFileReq(ID_RECV, remotePath.getBytes(AdbHelper.DEFAULT_CHARSET)), sameFileCheckHandler,
            getTimeOut(), MILLISECONDS);
        // read the result, in a byte array containing 2 ints (id, size)
        sameFileCheckHandler.waitRespondBegin(getTimeOut(), MILLISECONDS);
        return sameFileCheckHandler.waitFinish();
    }

    /**
     * Push multiple files
     *
     * @param fileArray file array
     * @param remotePath remote path
     * @param monitor monitor
     * @throws SyncException if file could not be pushed
     * @throws IOException in case of I/O error on the connection.
     * @throws TimeoutException in case of a timeout reading responses from the device.
     */
    private void doPush(File[] fileArray, String remotePath, ISyncProgressMonitor monitor)
        throws SyncException, IOException, TimeoutException {
        for (File f : fileArray) {
            // check if we're canceled
            if (monitor.isCanceled()) {
                throw new SyncException(SyncError.CANCELED);
            }
            if (f.exists()) {
                if (f.isDirectory()) {
                    // append the name of the directory to the remote path
                    String dest = remotePath + "/" + f.getName();
                    monitor.startSubTask(dest);
                    doPush(f.listFiles(), dest, monitor);

                    monitor.advance(1);
                } else if (f.isFile()) {
                    // append the name of the file to the remote path
                    String remoteFile = remotePath + "/" + f.getName();
                    monitor.startSubTask(remoteFile);
                    doPushFile(f.getAbsolutePath(), remoteFile, monitor);
                }
            }
        }
    }

    /**
     * Push a single file
     *
     * @param localPath the local file to push
     * @param remotePath the remote file (length max is 1024)
     * @param monitor the monitor. The monitor must be started already.
     * @throws SyncException if file could not be pushed
     * @throws IOException in case of I/O error on the connection.
     * @throws TimeoutException in case of a timeout reading responses from the device.
     */
    private void doPushFile(String localPath, String remotePath, ISyncProgressMonitor monitor) throws SyncException,
        IOException, TimeoutException {
        File localFile = new File(localPath);
        try (FileInputStream inputStream = new FileInputStream(localFile)) {
            doPushFile(inputStream, remotePath, monitor, FilePermissionUtil.getFilePosixPermission(localFile),
                (int) (localFile.lastModified() / 1000));
        }
    }

    /**
     * Push a single file
     *
     * @param inputStream the local stream to push
     * @param remotePath the remote file (length max is 1024)
     * @param monitor the monitor. The monitor must be started already.
     * @throws SyncException if file could not be pushed
     * @throws IOException in case of I/O error on the connection.
     * @throws TimeoutException in case of a timeout reading responses from the device.
     */
    private void doPushFile(InputStream inputStream, String remotePath,
        ISyncProgressMonitor monitor, int mode, int time) throws SyncException, IOException, TimeoutException {
        if (remotePath.getBytes(AdbHelper.DEFAULT_CHARSET).length > REMOTE_PATH_MAX_LENGTH) {
            throw new SyncException(SyncError.REMOTE_PATH_LENGTH);
        }
        PushFileHandler pullFileHandler = new PushFileHandler();
        mChannel.syncSendAndHandle(createSendFileReq(ID_SEND, remotePath.getBytes(AdbHelper.DEFAULT_CHARSET), mode),
            pullFileHandler, getTimeOut(), MILLISECONDS);
        System.arraycopy(ID_DATA, 0, getBuffer(), 0, ID_DATA.length);
        while (true) {
            // check if we're canceled
            if (monitor.isCanceled()) {
                throw new SyncException(SyncError.CANCELED);
            }
            // read up to SYNC_DATA_MAX
            int readCount = inputStream.read(getBuffer(), 8, SYNC_DATA_MAX);
            if (readCount == -1) {
                // we reached the end of the file
                break;
            }
            // now send the data to the device
            // first write the amount read
            ArrayHelper.swap32bitsToArray(readCount, getBuffer(), 4);
            mChannel.syncSend(getBuffer(), 0, readCount + 8, getTimeOut(), MILLISECONDS);
            // and advance the monitor
            monitor.advance(readCount);
        }
        mChannel.syncSend(createReq(ID_DONE, time), getTimeOut(), MILLISECONDS);
        pullFileHandler.waitFinish(getTimeOut(), MILLISECONDS);
    }

    /**
     * Returns the stat info of the remote file.
     *
     * @param path the remote file
     * @return an FileStat containing the mode, size and last modified info if all went well or null otherwise
     * @throws TimeoutException in case of a timeout reading responses from the device.
     */
    @Nullable
    public FileStat statFile(@NonNull String path) throws TimeoutException {
        StatFileHandler statFileHandler = new StatFileHandler();
        mChannel.syncSendAndHandle(createFileReq(ID_STAT, path), statFileHandler, getTimeOut(), MILLISECONDS);
        return statFileHandler.waitData(getTimeOut(), MILLISECONDS);
    }

    /**
     * Create a command with a code and an int values
     *
     * @param command command
     * @param value value
     * @return buffer
     */
    private static byte[] createReq(byte[] command, int value) {
        byte[] array = new byte[8];

        System.arraycopy(command, 0, array, 0, 4);
        ArrayHelper.swap32bitsToArray(value, array, 4);

        return array;
    }

    /**
     * Creates the data array for a stat request.
     *
     * @param command the 4 byte command (ID_STAT, ID_RECV, ...)
     * @param path The path of the remote file on which to execute the command
     * @return the byte[] to send to the device through adb
     */
    @SuppressWarnings("SameParameterValue")
    private static byte[] createFileReq(byte[] command, String path) {
        return createFileReq(command, path.getBytes(AdbHelper.DEFAULT_CHARSET));
    }

    /**
     * Creates the data array for a file request. This creates an array with a 4 byte command + the remote file name.
     *
     * @param command the 4 byte command (ID_STAT, ID_RECV, ...).
     * @param path The path, as a byte array, of the remote file on which to execute the command.
     * @return the byte[] to send to the device through adb
     */
    private static byte[] createFileReq(byte[] command, byte[] path) {
        byte[] array = new byte[8 + path.length];

        System.arraycopy(command, 0, array, 0, 4);
        ArrayHelper.swap32bitsToArray(path.length, array, 4);
        System.arraycopy(path, 0, array, 8, path.length);

        return array;
    }

    @SuppressWarnings("SameParameterValue")
    private static byte[] createSendFileReq(byte[] command, byte[] path, int mode) {
        // make the mode into a string
        String modeStr = "," + (mode & 0777);
        byte[] modeContent = modeStr.getBytes(AdbHelper.DEFAULT_CHARSET);
        byte[] array = new byte[8 + path.length + modeContent.length];

        System.arraycopy(command, 0, array, 0, 4);
        ArrayHelper.swap32bitsToArray(path.length + modeContent.length, array, 4);
        System.arraycopy(path, 0, array, 8, path.length);
        System.arraycopy(modeContent, 0, array, 8 + path.length, modeContent.length);

        return array;

    }

    /**
     * Checks the result array starts with the provided code
     *
     * @param result The result array to check
     * @param code The 4 byte code.
     * @return true if the code matches.
     */
    private static boolean checkResult(byte[] result, byte[] code) {
        return !(result[0] != code[0] ||
            result[1] != code[1] ||
            result[2] != code[2] ||
            result[3] != code[3]);

    }

    private static int getFileType(int mode) {
        if ((mode & S_ISOCK) == S_ISOCK) {
            return FileListingService.TYPE_SOCKET;
        }

        if ((mode & S_IFLNK) == S_IFLNK) {
            return FileListingService.TYPE_LINK;
        }

        if ((mode & S_IFREG) == S_IFREG) {
            return FileListingService.TYPE_FILE;
        }

        if ((mode & S_IFBLK) == S_IFBLK) {
            return FileListingService.TYPE_BLOCK;
        }

        if ((mode & S_IFDIR) == S_IFDIR) {
            return FileListingService.TYPE_DIRECTORY;
        }

        if ((mode & S_IFCHR) == S_IFCHR) {
            return FileListingService.TYPE_CHARACTER;
        }

        if ((mode & S_IFIFO) == S_IFIFO) {
            return FileListingService.TYPE_FIFO;
        }

        return FileListingService.TYPE_OTHER;
    }

    /**
     * Retrieve the buffer, allocating if necessary
     *
     * @return buffer
     */
    private byte[] getBuffer() {
        if (mBuffer == null) {
            // create the buffer used to read.
            // we read max SYNC_DATA_MAX, but we need 2 4 bytes at the beginning.
            mBuffer = new byte[SYNC_DATA_MAX + 8];
        }
        return mBuffer;
    }

    public enum State {
        /**
         * Protocol receiver state
         */
        WAIT_HEADER,
        WAIT_DATA,
        WAIT_ERROR_MESSAGE
    }
}
