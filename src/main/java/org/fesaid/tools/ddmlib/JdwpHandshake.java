package org.fesaid.tools.ddmlib;

import java.nio.ByteBuffer;

public class JdwpHandshake {
  // results from findHandshake
  public static final int HANDSHAKE_GOOD = 1;
  public static final int HANDSHAKE_NOTYET = 2;
  public static final int HANDSHAKE_BAD = 3;
  // this is sent and expected at the start of a JDWP connection
  private static final byte[] HANDSHAKE = {
      'J', 'D', 'W', 'P', '-', 'H', 'a', 'n', 'd', 's', 'h', 'a', 'k', 'e'
  };
  public static final int HANDSHAKE_LEN = HANDSHAKE.length;

  /**
   * Like findPacket(), but when we're expecting the JDWP handshake.
   *
   * Returns one of:
   *   HANDSHAKE_GOOD   - found handshake, looks good
   *   HANDSHAKE_BAD    - found enough data, but it's wrong
   *   HANDSHAKE_NOTYET - not enough data has been read yet
   */
  static int findHandshake(ByteBuffer buf) {
      int count = buf.position();
      int i;

      if (count < HANDSHAKE.length)
          return HANDSHAKE_NOTYET;

      for (i = HANDSHAKE.length - 1; i >= 0; --i) {
          if (buf.get(i) != HANDSHAKE[i])
              return HANDSHAKE_BAD;
      }

      return HANDSHAKE_GOOD;
  }

  /**
   * Remove the handshake string from the buffer.
   *
   * On entry and exit, "position" is the #of bytes in the buffer.
   */
  static void consumeHandshake(ByteBuffer buf) {
      // in theory, nothing else can have arrived, so this is overkill
      buf.flip();         // limit<-posn, posn<-0
      buf.position(HANDSHAKE.length);
      buf.compact();      // shift posn...limit, posn<-pending data
  }

  /**
   * Copy the handshake string into the output buffer.
   *
   * On exit, "buf"s position will be advanced.
   */
  static void putHandshake(ByteBuffer buf) {
      buf.put(HANDSHAKE);
  }
}
