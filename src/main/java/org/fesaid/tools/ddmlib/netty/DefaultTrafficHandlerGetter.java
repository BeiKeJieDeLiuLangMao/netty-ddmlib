package org.fesaid.tools.ddmlib.netty;

import io.netty.handler.traffic.GlobalTrafficShapingHandler;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class DefaultTrafficHandlerGetter implements TrafficHandlerGetter {

    @Override
    public GlobalTrafficShapingHandler getDeviceTrafficHandler(String serialNumber) {
        return null;
    }

    @Override
    public GlobalTrafficShapingHandler getGlobalTrafficHandler() {
        return null;
    }
}
