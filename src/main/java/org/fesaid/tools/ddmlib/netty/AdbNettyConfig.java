package org.fesaid.tools.ddmlib.netty;

import io.netty.util.NettyRuntime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@AllArgsConstructor
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class AdbNettyConfig {
    private String eventExecutorGroupPrefix = "AdbBoss";
    private int eventExecutorGroupThreadSize = 1;
    private String eventLoopGroupWorkerPrefix = "AdbWorker";
    private String proxyEventLoopGroupWorkerPrefix = "AdbProxyWorker";
    private int eventLoopGroupWorkerThreadSize = NettyRuntime.availableProcessors();
    private int connectTimeoutMills = 10000;
    private TrafficHandlerGetter trafficHandlerGetter = new DefaultTrafficHandlerGetter();
}
