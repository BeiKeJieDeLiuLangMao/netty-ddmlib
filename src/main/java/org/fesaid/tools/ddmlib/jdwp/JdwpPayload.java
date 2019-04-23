package org.fesaid.tools.ddmlib.jdwp;

import com.android.annotations.NonNull;

import java.nio.ByteBuffer;

public abstract class JdwpPayload {

    public abstract void parse(@NonNull  ByteBuffer buffer, @NonNull JdwpProtocol protocol);
}
