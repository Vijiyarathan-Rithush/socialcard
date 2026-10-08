package com.rvi.domain;

/** Constants belonging to the NDEF record wire format, without tag-specific TLV framing. */
public final class NdefFormat
{
    public static final int MESSAGE_BEGIN = 0x80;
    public static final int MESSAGE_END = 0x40;
    public static final int CHUNKED = 0x20;
    public static final int SHORT_RECORD = 0x10;
    public static final int ID_LENGTH_PRESENT = 0x08;
    public static final int TYPE_NAME_FORMAT_MASK = 0x07;
    public static final int WELL_KNOWN_TYPE = 0x01;
    public static final int URI_TYPE = 0x55;
    public static final int URI_TYPE_LENGTH = 1;
    public static final int URI_PREFIX_LENGTH = 1;
    public static final int MAX_SHORT_PAYLOAD_BYTES = 255;
    public static final int SHORT_RECORD_HEADER_BYTES = 3;
    public static final int NORMAL_RECORD_HEADER_BYTES = 6;
    public static final int BITS_PER_BYTE = 8;
    public static final int NORMAL_PAYLOAD_LENGTH_BYTES = Integer.BYTES;

    private NdefFormat()
    {
    }
}
