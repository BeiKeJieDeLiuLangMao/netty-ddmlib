package org.fesaid.tools.ddmlib;

import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.fesaid.tools.ddmlib.netty.AdbNettyConfig;
import org.fesaid.tools.ddmlib.netty.TrafficHandlerGetter;
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
    private IDevice device;

    static {
        EXECUTOR = new ScheduledThreadPoolExecutor(THREAD_SIZE, new DefaultThreadFactory(THREAD_NAME_PREFIX, THREAD_SIZE));
        GLOBAL_TRAFFIC_HANDLER = new GlobalTrafficShapingHandler(EXECUTOR,
            100 * 1024 * 1024, 100 * 1024 * 1024);
    }

    @Before
    public synchronized void initDevice() throws InterruptedException {
        AdbNettyConfig adbNettyConfig = new AdbNettyConfig();
        adbNettyConfig.setTrafficHandlerGetter(this);
        AndroidDebugBridge.addDeviceChangeListener(this);
        AndroidDebugBridge.initIfNeeded(false, adbNettyConfig);
        AndroidDebugBridge.createBridge();
        while (true) {
            if (device == null) {
                Thread.sleep(1);
            } else {
                break;
            }
        }
    }

    @Override
    public GlobalTrafficShapingHandler getDeviceTrafficHandler(String serialNumber) {
        synchronized (DEVICE_TRAFFIC_HANDLER) {
            if (!DEVICE_TRAFFIC_HANDLER.containsKey(serialNumber)) {
                DEVICE_TRAFFIC_HANDLER.put(serialNumber, new GlobalTrafficShapingHandler(EXECUTOR,
                    1024 * 1024, 1024 * 1024));
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
        this.device = device;
    }

    @Override
    public void deviceDisconnected(IDevice device) {
        log.info("deviceDisconnected, {}", device);
        this.device = device;
    }

    @Override
    public void deviceChanged(IDevice device, int changeMask) {
        log.info("deviceChanged, {}", device);
        this.device = device;
    }

    static class TrafficCounterTracker implements Runnable {
        @Override
        public void run() {
            DEVICE_TRAFFIC_HANDLER.forEach((serialNumber, handler) ->
                log.info("{}: read :{}, write: {}", serialNumber, handler.trafficCounter().lastReadThroughput(),
                    handler.trafficCounter().lastWriteThroughput())
            );
            log.info("Global: read: {}, write: {}", GLOBAL_TRAFFIC_HANDLER.trafficCounter().lastReadThroughput(),
                GLOBAL_TRAFFIC_HANDLER.trafficCounter().lastWriteThroughput());
        }
    }

    @Test
    public void testTrafficLimit() throws InterruptedException {
        EXECUTOR.scheduleAtFixedRate(new TrafficCounterTracker(), 1, 1, TimeUnit.SECONDS);
        Thread.sleep(100000);
    }

    @Test
    public void testMonitor() throws InterruptedException {
        Thread.sleep(100000);
    }

    @Test
    public void testScreenshot() throws AdbCommandRejectedException, IOException, TimeoutException {
        device.getScreenshot();
    }

    @Test
    public void testPushFile() throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
        device.pushFile(Objects.requireNonNull(this.getClass().getClassLoader().getResource("testFile.jpg"))
            .getFile(), "/data/local/tmp/testFile.jpg");
    }

    @Test
    public void testPushStream() throws IOException, TimeoutException, AdbCommandRejectedException, SyncException {
        device.pushFile(new FileInputStream(Objects.requireNonNull(this.getClass().getClassLoader()
            .getResource("testFile.jpg")).getFile()), "/data/local/tmp/testFile.jpg", MODE);
    }

    @Test
    public void testPullFile() throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
        testPushFile();
        device.pullFile("/data/local/tmp/testFile.jpg", System.getProperty("java.io.tmpdir") + "testFile.jpg");
        System.out.println(System.getProperty("java.io.tmpdir") + "testFile.jpg");
    }

}