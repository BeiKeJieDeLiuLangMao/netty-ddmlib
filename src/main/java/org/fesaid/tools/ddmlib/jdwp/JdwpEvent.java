package org.fesaid.tools.ddmlib.jdwp;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.fesaid.tools.ddmlib.IDevice;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
@Data
@AllArgsConstructor
public class JdwpEvent {
    private IDevice device;
    private Set<Integer> newPids;
}
