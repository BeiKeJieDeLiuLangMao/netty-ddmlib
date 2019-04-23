package org.fesaid.tools.ddmlib.jdwp;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.fesaid.tools.ddmlib.JdwpPacket;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class JdwpAgent {

    /**
     * Interceptors waiting for a specific reply id.
     */
    @NonNull
    private final ConcurrentMap<Integer, JdwpInterceptor> mReplyInterceptors;

    @NonNull
    private final List<JdwpInterceptor> mInterceptors;

    @NonNull
    private final JdwpProtocol mProtocol;

    public JdwpAgent(@NonNull JdwpProtocol protocol) {
        mReplyInterceptors = new ConcurrentHashMap<Integer, JdwpInterceptor>();
        mInterceptors = new LinkedList<JdwpInterceptor>();
        mProtocol = protocol;
    }

    /**
     * Adds an interceptor for a specific reply id. Once this interceptor
     * handles the response, it will be removed.
     */
    protected void addReplyInterceptor(int id, @NonNull JdwpInterceptor interceptor) {
        mReplyInterceptors.put(id, interceptor);
    }

    /**
     * Removes, if present, the interceptor to handle a reply with the given id.
     */
    protected void removeReplyInterceptor(int id) {
        mReplyInterceptors.remove(id);
    }

    public void clear() {
        mReplyInterceptors.clear();
    }

    public void addJdwpInterceptor(@NonNull JdwpInterceptor interceptor) {
        mInterceptors.add(interceptor);
    }

    public void removeJdwpInterceptor(@NonNull JdwpInterceptor interceptor) {
        mInterceptors.remove(interceptor);
    }

    public void incoming(@NonNull JdwpPacket packet, @Nullable JdwpAgent target) throws IOException {
        mProtocol.incoming(packet, target);
        int id = packet.getId();
        if (packet.isReply()) {
            JdwpInterceptor interceptor = mReplyInterceptors.remove(id);
            if (interceptor != null) {
                packet = interceptor.intercept(this, packet);
            }
        }
        for (JdwpInterceptor interceptor : mInterceptors) {
            if (packet == null) break;
            packet = interceptor.intercept(this, packet);
        }

        if (target != null && packet != null) {
            target.send(packet);
        }
    }

    public void send(@NonNull JdwpPacket packet, @NonNull JdwpInterceptor interceptor) throws IOException {
        mReplyInterceptors.put(packet.getId(), interceptor);
        send(packet);
    }

    protected abstract void send(@NonNull JdwpPacket packet) throws IOException;

    @NonNull
    public JdwpProtocol getJdwpProtocol() {
        return mProtocol;
    }
}
