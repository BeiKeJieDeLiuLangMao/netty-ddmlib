package org.fesaid.tools.ddmlib.jdwp.packets;

import com.android.annotations.NonNull;
import org.fesaid.tools.ddmlib.jdwp.JdwpPayload;
import org.fesaid.tools.ddmlib.jdwp.JdwpProtocol;

import java.nio.ByteBuffer;

public class IdSizesReply extends JdwpPayload {

    public int fieldIDSize;
    public int methodIDSize;
    public int objectIDSize;
    public int refTypeIDSize;
    public int frameIDSize;

    @Override
    public void parse(@NonNull ByteBuffer buffer, @NonNull JdwpProtocol protocol) {
        fieldIDSize = buffer.getInt();
        methodIDSize = buffer.getInt();
        objectIDSize = buffer.getInt();
        refTypeIDSize = buffer.getInt();
        frameIDSize = buffer.getInt();
    }
}
