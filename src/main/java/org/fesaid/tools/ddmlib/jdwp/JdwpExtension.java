package org.fesaid.tools.ddmlib.jdwp;

import com.android.annotations.NonNull;
import org.fesaid.tools.ddmlib.Client;

public abstract class JdwpExtension {

    /**
     * Allows an extension to register interceptors to capture JDWP traffic.
     */
    public abstract void intercept(@NonNull Client client);
}
