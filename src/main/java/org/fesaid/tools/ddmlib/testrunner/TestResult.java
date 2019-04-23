package org.fesaid.tools.ddmlib.testrunner;

import java.util.Arrays;
import java.util.Map;

/**
 * Container for a result of a single test.
 */
public class TestResult {

    public enum TestStatus {
        /** Test failed. */
        FAILURE,
        /** Test passed */
        PASSED,
        /** Test started but not ended */
        INCOMPLETE,
        /** Test assumption failure */
        ASSUMPTION_FAILURE,
        /** Test ignored */
        IGNORED,
    }

    private TestStatus mStatus;
    private String mStackTrace;
    private Map<String, String> mMetrics;
    // the start and end time of the test, measured via {@link System#currentTimeMillis()}
    private long mStartTime = 0;
    private long mEndTime = 0;

    public TestResult() {
        mStatus = TestStatus.INCOMPLETE;
        mStartTime = System.currentTimeMillis();
    }

    /**
     * Get the {@link TestStatus} result of the test.
     */
    public TestStatus getStatus() {
        return mStatus;
    }

    /**
     * Get the associated {@link String} stack trace. Should be <code>null</code> if
     * {@link #getStatus()} is {@link TestStatus#PASSED}.
     */
    public String getStackTrace() {
        return mStackTrace;
    }

    /**
     * Get the associated test metrics.
     */
    public Map<String, String> getMetrics() {
        return mMetrics;
    }

    /**
     * Set the test metrics, overriding any previous values.
     */
    public void setMetrics(Map<String, String> metrics) {
        mMetrics = metrics;
    }

    /**
     * Return the {@link System#currentTimeMillis()} time that the
     * {@link ITestRunListener#testStarted(TestIdentifier)} event was received.
     */
    public long getStartTime() {
        return mStartTime;
    }

    /**
     * Allows to set the time when the test was started, to be used with {@link
     * ITestRunListener#testStarted(TestIdentifier, long)}.
     */
    public void setStartTime(long startTime) {
        mStartTime = startTime;
    }

    /**
     * Return the {@link System#currentTimeMillis()} time that the {@link
     * ITestRunListener#testEnded(TestIdentifier, Map)} event was received.
     */
    public long getEndTime() {
        return mEndTime;
    }

    /**
     * Set the {@link TestStatus}.
     */
    public TestResult setStatus(TestStatus status) {
       mStatus = status;
       return this;
    }

    /**
     * Set the stack trace.
     */
    public void setStackTrace(String trace) {
        mStackTrace = trace;
    }

    /**
     * Sets the end time
     */
    public void setEndTime(long currentTimeMillis) {
        mEndTime = currentTimeMillis;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] {mMetrics, mStackTrace, mStatus});
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        TestResult other = (TestResult) obj;
        return equal(mMetrics, other.mMetrics) &&
               equal(mStackTrace, other.mStackTrace) &&
               equal(mStatus, other.mStatus);
    }

    private static boolean equal(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }
}
