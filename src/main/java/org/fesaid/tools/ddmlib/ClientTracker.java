package org.fesaid.tools.ddmlib;

import com.android.annotations.NonNull;

/** Tracks device {@link Client clients} */
interface ClientTracker {
    void trackDisconnectedClient(@NonNull Client client);

    void trackClientToDropAndReopen(@NonNull Client client, int port);
}
