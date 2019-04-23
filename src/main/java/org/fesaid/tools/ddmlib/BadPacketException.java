package org.fesaid.tools.ddmlib;

/**
 * Thrown if the contents of a packet are bad.
 */
@SuppressWarnings("serial")
class BadPacketException extends RuntimeException {
    public BadPacketException()
    {
        super();
    }

    public BadPacketException(String msg)
    {
        super(msg);
    }
}

