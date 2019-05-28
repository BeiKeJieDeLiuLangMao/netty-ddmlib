package org.fesaid.tools.ddmlib;

import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.netty.AdbNettyConfig;
import org.fesaid.tools.ddmlib.netty.TrafficHandlerGetter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@Slf4j
public class IDeviceTest implements TrafficHandlerGetter, AndroidDebugBridge.IDeviceChangeListener {
    private static final int THREAD_SIZE = 1;
    private static final String THREAD_NAME_PREFIX = "TrafficHandler";
    private static final Map<String, GlobalTrafficShapingHandler> DEVICE_TRAFFIC_HANDLER = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService EXECUTOR;
    private static final GlobalTrafficShapingHandler GLOBAL_TRAFFIC_HANDLER;
    @SuppressWarnings("OctalInteger")
    private static final int MODE = 0664;
    private static IDevice device;

    static {
        EXECUTOR = new ScheduledThreadPoolExecutor(THREAD_SIZE, new DefaultThreadFactory(THREAD_NAME_PREFIX, THREAD_SIZE));
        GLOBAL_TRAFFIC_HANDLER = new GlobalTrafficShapingHandler(EXECUTOR, 100 * 1024 * 1024, 100 * 1024 * 1024);
    }

    @Before
    public synchronized void initDevice() throws InterruptedException {
        if (device == null) {
            log.info("Begin init device");
            AdbNettyConfig adbNettyConfig = new AdbNettyConfig();
            adbNettyConfig.setTrafficHandlerGetter(this);
            AndroidDebugBridge.addDeviceChangeListener(this);
            AndroidDebugBridge.initIfNeeded(false, adbNettyConfig);
            AndroidDebugBridge.createBridge();
            while (true) {
                if (device == null) {
                    Thread.sleep(100);
                    log.debug("wait device...");
                } else {
                    break;
                }
            }
        }
    }

    @Override
    public GlobalTrafficShapingHandler getDeviceTrafficHandler(String serialNumber) {
        synchronized (DEVICE_TRAFFIC_HANDLER) {
            if (!DEVICE_TRAFFIC_HANDLER.containsKey(serialNumber)) {
                DEVICE_TRAFFIC_HANDLER.put(serialNumber, new GlobalTrafficShapingHandler(EXECUTOR, 100 * 1024 * 1024, 100 * 1024 * 1024));
            }
            return DEVICE_TRAFFIC_HANDLER.get(serialNumber);
        }
    }

    @Override
    public GlobalTrafficShapingHandler getGlobalTrafficHandler() {
        return GLOBAL_TRAFFIC_HANDLER;
    }

    @Override
    public void deviceConnected(IDevice device) {
        log.info("deviceConnected, {}", device);
        IDeviceTest.device = device;
    }

    @Override
    public void deviceDisconnected(IDevice device) {
        log.info("deviceDisconnected, {}", device);
        IDeviceTest.device = device;
    }

    @Override
    public void deviceChanged(IDevice device, int changeMask) {
        log.info("deviceChanged, {}", device);
        IDeviceTest.device = device;
    }

    static class TrafficCounterTracker implements Runnable {
        @Override
        public void run() {
            DEVICE_TRAFFIC_HANDLER.forEach((serialNumber, handler) ->
                log.info("{}: read :{}, write: {}", serialNumber, handler.trafficCounter().lastReadThroughput(), handler.trafficCounter().lastWriteThroughput()));
            log.info("Global: read: {}, write: {}", GLOBAL_TRAFFIC_HANDLER.trafficCounter().lastReadThroughput(), GLOBAL_TRAFFIC_HANDLER.trafficCounter().lastWriteThroughput());
        }
    }

    @Test
    public void testTrafficLimit() throws AdbCommandRejectedException, IOException, TimeoutException {
        log.info("testTrafficLimit begin");
        Future future = EXECUTOR.scheduleAtFixedRate(new TrafficCounterTracker(), 1, 1, TimeUnit.SECONDS);
        device.getScreenshot();
        future.cancel(true);
        System.gc();
        log.info("testTrafficLimit end");
    }

    @Test
    public void testMonitor() throws InterruptedException {
        log.info("testMonitor begin");
        Thread.sleep(1000);
        System.gc();
        log.info("testMonitor end");
    }

    @Test
    public void testScreenshot() throws AdbCommandRejectedException, IOException, TimeoutException {
        log.info("testScreenshot begin");
        device.getScreenshot();
        System.gc();
        log.info("testScreenshot end");
    }

    @Test
    public void testPushFile() throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
        log.info("testPushFile begin");
        device.pushFile(Objects.requireNonNull(this.getClass().getClassLoader().getResource("testFile.jpg")).getFile(), "/data/local/tmp/testFile.jpg");
        System.gc();
        log.info("testPushFile end");
    }

    @Test
    public void testPushStream() throws IOException, TimeoutException, AdbCommandRejectedException, SyncException {
        log.info("testPushStream begin");
        device.pushFile(new FileInputStream(Objects.requireNonNull(this.getClass().getClassLoader().getResource("testFile.jpg")).getFile()), "/data/local/tmp/testFile.jpg", MODE);
        System.gc();
        log.info("testPushStream end");
    }

    @Test
    public void testPullFile() throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
        log.info("testPullFile begin");
        device.pushFile(Objects.requireNonNull(this.getClass().getClassLoader().getResource("testFile.jpg")).getFile(), "/data/local/tmp/testFile.jpg");
        device.pullFile("/data/local/tmp/testFile.jpg", System.getProperty("java.io.tmpdir") + "testFile.jpg");
        System.gc();
        log.info("testPullFile end");
    }

    @Test
    public void testIsSameFile() throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
        log.info("testIsSameFile begin");
        String remote = "/data/local/tmp/testFile.jpg";
        device.pushFile(Objects.requireNonNull(this.getClass().getClassLoader().getResource("testFile.jpg")).getFile(), remote);
        Assert.assertTrue(device.isSameWithFile(remote, this.getClass().getClassLoader().getResourceAsStream("testFile.jpg")));
        Assert.assertFalse(device.isSameWithFile(remote, new ByteArrayInputStream(new byte[1])));
        log.info("testIsSameFile end");
    }

}