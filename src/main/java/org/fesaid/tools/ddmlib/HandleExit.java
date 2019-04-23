package org.fesaid.tools.ddmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Submit an exit request.
 */
final class HandleExit extends ChunkHandler {

    public static final int CHUNK_EXIT = type("EXIT");

    private static final HandleExit mInst = new HandleExit();


    private HandleExit() {}

    /**
     * Register for the packets we expect to get from the client.
     */
    public static void register(MonitorThread mt) {}

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
        handleUnknownChunk(client, type, data, isReply, msgId);
    }

    /**
     * Send an EXIT request to the client.
     */
    public static void sendEXIT(Client client, int status)
        throws IOException
    {
        ByteBuffer rawBuf = allocBuffer(4);
        JdwpPacket packet = new JdwpPacket(rawBuf);
        ByteBuffer buf = getChunkDataBuf(rawBuf);

        buf.putInt(status);

        finishChunkPacket(packet, CHUNK_EXIT, buf.position());
        Log.d("ddm-exit", "Sending " + name(CHUNK_EXIT) + ": " + status);
        client.send(packet, mInst);
    }
}

