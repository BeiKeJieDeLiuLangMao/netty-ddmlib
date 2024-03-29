package org.fesaid.tools.ddmlib;

import java.util.concurrent.TimeUnit;

public class ScreenRecorderOptions {
    // video size is given by width x height, defaults to device's main display resolution
    // or 1280x720.
    public final int width;
    public final int height;

    // bit rate in Mbps. Defaults to 4Mbps
    public final int bitrateMbps;

    // time limit, maximum of 3 seconds
    public final long timeLimit;
    public final TimeUnit timeLimitUnits;

    // display touches
    public final boolean showTouches;

    private ScreenRecorderOptions(Builder builder) {
        width = builder.mWidth;
        height = builder.mHeight;

        bitrateMbps = builder.mBitRate;

        timeLimit = builder.mTime;
        timeLimitUnits = builder.mTimeUnits;

        showTouches = builder.mShowTouches;
    }

    public static class Builder {
        private int mWidth;
        private int mHeight;
        private int mBitRate;
        private boolean mShowTouches;
        private long mTime;
        private TimeUnit mTimeUnits;

        public Builder setSize(int w, int h) {
            mWidth = w;
            mHeight = h;
            return this;
        }

        public Builder setBitRate(int bitRateMbps) {
            mBitRate = bitRateMbps;
            return this;
        }

        public Builder setTimeLimit(long time, TimeUnit units) {
            mTime = time;
            mTimeUnits = units;
            return this;
        }

        public Builder setShowTouches(boolean showTouches) {
            mShowTouches = showTouches;
            return this;
        }

        public ScreenRecorderOptions build() {
            return new ScreenRecorderOptions(this);
        }
    }
}
