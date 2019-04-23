package org.fesaid.tools.ddmlib;

import org.fesaid.tools.ddmlib.ClientData.DebuggerStatus;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Handle the "wait" chunk (WAIT).  These are sent up when the client is
 * waiting for something, e.g. for a debugger to attach.
 */
final class HandleWait extends ChunkHandler {

    public static final int CHUNK_WAIT = type("WAIT");

    private static final HandleWait mInst = new HandleWait();


    private HandleWait() {}

    /**
     * Register for the packets we expect to get from the client.
     */
    public static void register(MonitorThread mt) {
        mt.registerChunkHandler(CHUNK_WAIT, mInst);
    }

    /**
     * Client is ready.
     */
    @Override
    public void clientReady(Client client) throws IOException {}

    /**
     * Client went away.
     */
    @Override
    public void clientDisconnected(Client client) {}

    /**
     * Chunk handler entry point.
     */
    @Override
    public void handleChunk(Client client, int type, ByteBuffer data, boolean isReply, int msgId) {

        Log.d("ddm-wait", "handling " + name(type));

        if (type == CHUNK_WAIT) {
            assert !isReply;
            handleWAIT(client, data);
        } else {
            handleUnknownChunk(client, type, data, isReply, msgId);
        }
    }

    /*
     * Handle a reply to our WAIT message.
     */
    private static void handleWAIT(Client client, ByteBuffer data) {
        byte reason;

        reason = data.get();

        Log.d("ddm-wait", "WAIT: reason=" + reason);


        ClientData cd = client.getClientData();
        synchronized (cd) {
            cd.setDebuggerConnectionStatus(DebuggerStatus.WAITING);
        }

        client.update(Client.CHANGE_DEBUGGER_STATUS);
    }
}

