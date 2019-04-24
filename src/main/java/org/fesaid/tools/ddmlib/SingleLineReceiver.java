package org.fesaid.tools.ddmlib;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SingleLineReceiver extends MultiLineReceiver {

    private StringBuilder sb = new StringBuilder();
    private CountDownLatch c = new CountDownLatch(1);

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void processNewLines(String[] lines) {
        Arrays.stream(lines).forEach(sb::append);
    }

    @Override
    public void done() {
        c.countDown();
    }

    public String get(long timeout, TimeUnit unit) throws TimeoutException {
        try {
            if (c.await(timeout, unit)) {
                return sb.toString();
            }
        } catch (InterruptedException e) {
            //ignore.
        }
        throw new TimeoutException();
    }

}