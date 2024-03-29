package org.fesaid.tools.ddmlib;

import com.android.annotations.NonNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdbVersion implements Comparable<AdbVersion> {
    public static final AdbVersion UNKNOWN = new AdbVersion(-1, -1, -1);

    /** Matches e.g. ".... 1.0.32" */
    private static final Pattern ADB_VERSION_PATTERN = Pattern.compile(
            "^.*(\\d+)\\.(\\d+)\\.(\\d+).*");

    public final int major;
    public final int minor;
    public final int micro;

    private AdbVersion(int major, int minor, int micro) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%1$d.%2$d.%3$d", major, minor, micro);
    }

    @Override
    public int compareTo(@NonNull AdbVersion o) {
        if (major != o.major) {
            return major - o.major;
        }

        if (minor != o.minor) {
            return minor - o.minor;
        }

        return micro - o.micro;
    }

    @NonNull
    public static AdbVersion parseFrom(@NonNull String input) {
        Matcher matcher = ADB_VERSION_PATTERN.matcher(input);
        if (matcher.matches()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int micro = Integer.parseInt(matcher.group(3));
            return new AdbVersion(major, minor, micro);
        } else {
            return UNKNOWN;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AdbVersion version = (AdbVersion) o;

        if (major != version.major) {
            return false;
        }
        if (minor != version.minor) {
            return false;
        }
        return micro == version.micro;

    }

    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + micro;
        return result;
    }
}
