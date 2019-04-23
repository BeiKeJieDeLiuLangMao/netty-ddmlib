package org.fesaid.tools.ddmlib.logcat;

import java.util.List;

public interface LogCatListener {
    void log(List<LogCatMessage> msgList);
}
