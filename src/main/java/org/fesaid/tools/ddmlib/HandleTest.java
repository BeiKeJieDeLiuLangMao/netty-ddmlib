package org.fesaid.tools.ddmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Handle thread status updates.
 */
final class HandleTest extends ChunkHandler {

    public static final int CHUNK_TEST = type("TEST");

    private static final HandleTest mInst = new HandleTest();


    private HandleTest() {}

    /**
     * Register for the packets we expect to get from the client.
     */
    public static void register(MonitorThread mt) {
        mt.registerChunkHandler(CHUNK_TEST, mInst);
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

        Log.d("ddm-test", "handling " + name(type));

        if (type == CHUNK_TEST) {
            handleTEST(client, data);
        } else {
            handleUnknownChunk(client, type, data, isReply, msgId);
        }
    }

    /*
     * Handle a thread creation message.
     */
    private void handleTEST(Client client, ByteBuffer data)
    {
        /*
         * Can't call data.array() on a read-only ByteBuffer, so we make
         * a copy.
         */
        byte[] copy = new byte[data.limit()];
        data.get(copy);

        Log.d("ddm-test", "Received:");
        Log.hexDump("ddm-test", Log.LogLevel.DEBUG, copy, 0, copy.length);
    }
}

