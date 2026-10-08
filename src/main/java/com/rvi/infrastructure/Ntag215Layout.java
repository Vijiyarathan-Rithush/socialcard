package com.rvi.infrastructure;

/** Physical NTAG215 addresses, distinct from the NFC Forum NDEF data-area capacity. */
final class Ntag215Layout
{
    static final int PAGE_BYTES = 4;
    static final int STATIC_LOCK_PAGE = 2;
    static final int CC_PAGE = 3;
    static final int FIRST_DATA_PAGE = 4;
    static final int DATA_BYTES = 496;
    static final int LAST_DATA_PAGE = FIRST_DATA_PAGE + DATA_BYTES / PAGE_BYTES - 1;
    static final int DYNAMIC_LOCK_PAGE = 130;
    static final int CONFIG_PAGE = 131;
    static final int ACCESS_PAGE = 132;
    static final int LAST_CONFIG_PAGE = 134;
    static final int CC_MAGIC = 0xE1;
    static final int CC_VERSION_MAJOR = 0x10;
    static final int CC_SIZE_UNIT = 8;
    static final int NIBBLE_MASK = 0x0F;
    static final int HIGH_NIBBLE_MASK = 0xF0;
    static final int MIRROR_MODE_MASK = 0xC0;

    private Ntag215Layout()
    {
    }

    static int paddedSize(final int bytes)
    {
        return Math.ceilDiv(bytes, PAGE_BYTES) * PAGE_BYTES;
    }
}
