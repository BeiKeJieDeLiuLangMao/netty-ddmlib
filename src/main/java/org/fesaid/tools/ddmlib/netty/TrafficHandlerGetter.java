package org.fesaid.tools.ddmlib.netty;

import com.android.annotations.Nullable;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public interface TrafficHandlerGetter {
    /**
     * Get device traffic handler
     *
     * @param serialNumber device serial number
     * @return device traffic handler
     */
    @Nullable
    GlobalTrafficShapingHandler getDeviceTrafficHandler(String serialNumber);

    /**
     * Get global traffic handler
     *
     * @return global traffic handler
     */
    @Nullable GlobalTrafficShapingHandler getGlobalTrafficHandler();
}
