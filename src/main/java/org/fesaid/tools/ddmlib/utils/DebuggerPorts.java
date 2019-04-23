package org.fesaid.tools.ddmlib.utils;

import com.android.annotations.concurrency.GuardedBy;

import java.util.ArrayList;
import java.util.List;

public class DebuggerPorts {
    @GuardedBy("mDebuggerPorts")
    private final List<Integer> mDebuggerPorts = new ArrayList<Integer>();

    public DebuggerPorts(int basePort) {
        mDebuggerPorts.add(basePort);
    }

    public int next() {
        // get the first port and remove it
        synchronized (mDebuggerPorts) {
            if (!mDebuggerPorts.isEmpty()) {
                int port = mDebuggerPorts.get(0);

                // remove it.
                mDebuggerPorts.remove(0);

                // if there's nothing left, add the next port to the list
                if (mDebuggerPorts.isEmpty()) {
                    mDebuggerPorts.add(port+1);
                }

                return port;
            }
        }

        return -1;
    }

    public void free(int port) {
        if (port <= 0) {
            return;
        }

        synchronized (mDebuggerPorts) {
            // because there could be case where clients are closed twice, we have to make
            // sure the port number is not already in the list.
            if (mDebuggerPorts.indexOf(port) == -1) {
                // add the port to the list while keeping it sorted. It's not like there's
                // going to be tons of objects so we do it linearly.
                int count = mDebuggerPorts.size();
                for (int i = 0; i < count; i++) {
                    if (port < mDebuggerPorts.get(i)) {
                        mDebuggerPorts.add(i, port);
                        break;
                    }
                }
                // TODO: check if we can compact the end of the list.
            }
        }
    }
}
