package org.fesaid.tools.ddmlib;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * Handle the "app name" chunk (APNM).
 */
final class HandleAppName extends ChunkHandler {

    public static final int CHUNK_APNM = type("APNM");

    private static final HandleAppName mInst = new HandleAppName();


    private HandleAppName() {}

    /**
     * Register for the packets we expect to get from the client.
     */
    public static void register(MonitorThread mt) {
        mt.registerChunkHandler(CHUNK_APNM, mInst);
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
    public void handleChunk(Client client, int type, ByteBuffer data,
            boolean isReply, int msgId) {

        Log.d("ddm-appname", "handling " + name(type));

        if (type == CHUNK_APNM) {
            assert !isReply;
            handleAPNM(client, data);
        } else {
            handleUnknownChunk(client, type, data, isReply, msgId);
        }
    }

    /*
     * Handle a reply to our APNM message.
     */
    private static void handleAPNM(Client client, ByteBuffer data) {
        int appNameLen;
        String appName;

        appNameLen = data.getInt();
        appName = ByteBufferUtil.getString(data, appNameLen);

        // Newer devices send user id in the APNM packet.
        int userId = -1;
        boolean validUserId = false;
        if (data.hasRemaining()) {
            try {
                userId = data.getInt();
                validUserId = true;
            } catch (BufferUnderflowException e) {
                // two integers + utf-16 string
                int expectedPacketLength = 8 + appNameLen * 2;

                Log.e("ddm-appname", "Insufficient data in APNM chunk to retrieve user id.");
                Log.e("ddm-appname", "Actual chunk length: " + data.capacity());
                Log.e("ddm-appname", "Expected chunk length: " + expectedPacketLength);
            }
        }

        Log.d("ddm-appname", "APNM: app='" + appName + "'");

        ClientData cd = client.getClientData();
        synchronized (cd) {
            cd.setClientDescription(appName);

            if (validUserId) {
                cd.setUserId(userId);
            }
        }

        client = checkDebuggerPortForAppName(client, appName);

        if (client != null) {
            client.update(Client.CHANGE_NAME);
        }
    }
 }

