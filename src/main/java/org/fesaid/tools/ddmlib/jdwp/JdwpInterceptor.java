package org.fesaid.tools.ddmlib.jdwp;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.fesaid.tools.ddmlib.JdwpPacket;

public abstract class JdwpInterceptor {

    @Nullable
    public abstract JdwpPacket intercept(@NonNull JdwpAgent agent, @NonNull JdwpPacket packet);
}
