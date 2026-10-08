package com.rvi.domain;

public final class NdefFormat
{
    public static final int TLV_TYPE = 0x03;
    public static final int TERMINATOR = 0xFE;
    public static final int EXTENDED_LENGTH = 0xFF;
    public static final int MAX_MESSAGE_BYTES = 254;
    public static final int TLV_HEADER_BYTES = 2;
    public static final int TLV_OVERHEAD = 3;
    public static final int RECORD_OVERHEAD = 4;
    public static final int MIN_RECORD_BYTES = 5;
    public static final int TYPE_LENGTH = 1;
    public static final int URI_TYPE = 0x55;
    public static final int HTTPS_PREFIX = 0x04;
    public static final int TYPE_LENGTH_OFFSET = 1;
    public static final int PAYLOAD_LENGTH_OFFSET = 2;
    public static final int URI_TYPE_OFFSET = 3;
    public static final int PREFIX_OFFSET = 4;
    public static final int URL_OFFSET = 5;
    public static final int BEGIN_MASK = 0x80;
    public static final int END_MASK = 0x40;
    public static final int FORMAT_MASK = 0x3F;
    public static final int URI_RECORD_FORMAT = 0x11;
    public static final int SINGLE_HEADER = 0xD1;
    public static final int FIRST_HEADER = 0x91;
    public static final int LAST_HEADER = 0x51;
    public static final int MIDDLE_HEADER = 0x11;
    public static final String HTTPS = "https://";

    private NdefFormat()
    {
    }
}
