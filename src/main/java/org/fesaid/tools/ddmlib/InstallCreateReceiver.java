package org.fesaid.tools.ddmlib;

import com.android.annotations.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Output receiver for "pm install-create" command line.
 *
 * <p>Extension of {@link InstallReceiver} that can receive a Success message
 * from ADB followed by a session ID.
 */
public class InstallCreateReceiver extends InstallReceiver {
    private static final String LOG_TAG = "InstallCreateReceiver";
    private static final Pattern successPattern = Pattern.compile("Success: .*\\[(\\d*)\\]");

    private String mSessionId = null;

    /**
     * Returns the session ID if install-create create session successfully. Returns {@code null} if
     * failure is seen.
     */
    @Nullable
    public String getSessionId() {
        if (getSuccessMessage() == null) {
            return null;
        }
        String output = getSuccessMessage();
        Matcher matcher = successPattern.matcher(output);
        if (matcher.matches()) {
            mSessionId = matcher.group(1);
        } else {
            mSessionId = null;
            Log.e(LOG_TAG, String.format("Output '%s' doesn't provide session id", output));
        }

        return mSessionId;
    }
}
