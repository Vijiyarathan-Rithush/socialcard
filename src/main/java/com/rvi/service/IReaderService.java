package com.rvi.service;

import com.rvi.application.NDefDecoder;
import com.rvi.domain.NDefContent;
import com.rvi.domain.NDefMessage;

import java.util.List;

/** Card operations on one connection. Use try-with-resources to release it. */
public interface IReaderService extends AutoCloseable
{
    /** Connects immediately. Place a card on the reader first; does not wait for insertion. */
    void connect();

    /** Releases the owned card connection. Safe to call repeatedly. */
    void disconnect();

    /** Encodes one absolute URI, including HTTP, HTTPS, tel and mailto; no hardware needed. */
    NDefMessage encodeURI(String message);

    /** Encodes a nonempty list of absolute URIs in order; no hardware needed. */
    NDefMessage encodeURIs(List<String> urls);

    /** Reads validated NDEF bytes. Requires connect(); use readContents() for typed values. */
    NDefMessage read();

    /** Replaces card content and verifies it by reading back. Requires connect(). */
    void write(NDefMessage message);

    /** Reads supported typed URI, text, contact and Wi-Fi records in order. */
    default List<NDefContent> readContents()
    {
        return new NDefDecoder().decode(read());
    }

    /** Reads URI strings in order, skipping other supported content types. */
    default List<String> readURIs()
    {
        return new NDefDecoder().decodeURIs(read());
    }

    /** Encodes and writes a single absolute URI, replacing existing content. Requires connect(). */
    default void writeURI(final String uri)
    {
        write(encodeURI(uri));
    }

    /** Releases the card connection, including when leaving a try-with-resources block. */
    @Override
    void close();
}
