/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.io;

import java.io.Closeable;
import java.io.IOException;

/**
 * An interface allowing random access read operations.
 */
public interface RandomAccessRead extends Closeable
{
    /**
     * Read a single byte of data.
     *
     * @return The byte of data that is being read.
     *
     * @throws IOException If there is an error while reading the data.
     */
    int read() throws IOException;

    /**
     * Read a buffer of data.
     *
     * @param b The buffer to write the data to.
     * @return The number of bytes that were actually read.
     * @throws IOException If there was an error while reading the data.
     */
    default int read(byte[] b) throws IOException
    {
        return read(b, 0, b.length);
    }

    /**
     * Read a buffer of data.
     *
     * @param b The buffer to write the data to.
     * @param offset Offset into the buffer to start writing.
     * @param length The amount of data to attempt to read.
     * @return The number of bytes that were actually read.
     * @throws IOException If there was an error while reading the data.
     */
    int read(byte[] b, int offset, int length) throws IOException;

    /**
     * Read a buffer of data of exactly {@code length} bytes.
     * 
     * @throws IOException if less than {@code length} bytes are available
     */
    default byte[] readExact(byte[] b, int offset, int length) throws IOException
    {
        if (length() - getPosition() >= length)
        {
            int read = readUpTo(b, offset, length);
            if (read == length)
            {
                return b;
            }
            rewind(read);
        }
        throw new IOException("End-of-data");
    }

    /**
     * Read a buffer of data of exactly {@code length} bytes.
     * 
     * @throws IOException if less than {@code length} bytes are available
     */
    default byte[] readExact(int length) throws IOException
    {
        return readExact(new byte[length], 0, length);
    }

    /**
     * Finishes when {@code length} bytes are read, or EOF. Always returns {@code result}, never trims.
     * @see InputStream#readNBytes(byte[], int, int)
     * @return when {@code result.length} bytes are read, or EOF
     */
    default int readUpTo(byte[] result) throws IOException
    {
        return readUpTo(result, 0, result.length);
    }

    /**
     * Finishes when {@code length} bytes are read, or EOF. Always returns {@code byte[length]}, never trims.
     * @see InputStream#readNBytes(byte[], int, int)
     * @return when {@code length} bytes are read, or EOF
     */
    default byte[] readUpTo(int length) throws IOException
    {
        byte[] result = new byte[length];
        readUpTo(result, 0, result.length);
        return result;
    }

    /**
     * Finishes when {@code length} bytes are read, or EOF. Just like {@link org.apache.pdfbox.io.IOUtils#populateBuffer(java.io.InputStream, byte[])}
     * @see InputStream#readNBytes(byte[], int, int)
     * @return amount of read bytes
     */
    default int readUpTo(byte[] result, int offset, int length) throws IOException
    {
        if (Integer.MAX_VALUE - length < offset)
        {
            throw new IOException("Integer overflow");
        }
        int cursor = offset;
        int end = offset + length;
        while (cursor < end)
        {
            int read = read(result, cursor, end - cursor);
            if (read < 0)
            {
                break;
            }
            else if (read == 0)
            {
                // in order to not get stuck in a loop we check readBytes (this should never happen)
                throw new IOException("Read 0 bytes, risk of an infinite loop");
            }
            cursor += read;
        }
        return cursor - offset;
    }

    /**
     * Returns offset of next byte to be returned by a read method.
     * 
     * @return offset of next byte which will be returned with next {@link #read()} (if no more bytes are left it
     * returns a value &gt;= length of source)
     * 
     * @throws IOException If there was an error while getting the current position
     */
    long getPosition() throws IOException;
    
    /**
     * Seek to a position in the data.
     * 
     * @param position The position to seek to.
     * @throws IOException If there is an error while seeking.
     */
    void seek(long position) throws IOException;

    /**
     * The total number of bytes that are available.
     * 
     * @return The number of bytes available.
     *
     * @throws IOException If there is an IO error while determining the length of the data stream.
     */
    long length() throws IOException;

    /**
     * Returns true if this source has been closed.
     * 
     * @return true if the source has been closed
     */
    boolean isClosed();

    /**
     * This will peek at the next byte.
     *
     * @return The next byte on the stream, leaving it as available to read.
     *
     * @throws IOException If there is an error reading the next byte.
     */
    default int peek() throws IOException
    {
        int result = read();
        if (result != -1)
        {
            rewind(1);
        }
        return result;
    }

    /**
     * Seek backwards the given number of bytes.
     * 
     * @param bytes the number of bytes to be seeked backwards
     * @throws IOException If there is an error while seeking
     */
    default void rewind(int bytes) throws IOException
    {
        seek(getPosition() - bytes);
    }

    /**
     * A simple test to see if we are at the end of the data.
     *
     * @return true if we are at the end of the data.
     *
     * @throws IOException If there is an error reading the next byte.
     */
    boolean isEOF() throws IOException;

    /**
     * Returns an estimate of the number of bytes that can be read.
     *
     * @return the number of bytes that can be read
     * @throws IOException if this random access has been closed
     */
    default int available() throws IOException
    {
        return (int) Math.min(length() - getPosition(), Integer.MAX_VALUE);
    }

    /**
     * Skips a given number of bytes.
     *
     * @param length the number of bytes to be skipped
     * @throws IOException if an I/O error occurs while reading data
     */
    default void skip(int length) throws IOException
    {
        seek(getPosition() + length);
    }

    /**
     * Creates a random access read view starting at the given position with the given length.
     * 
     * @param startPosition start position within the underlying random access read
     * @param streamLength stream length
     * @return the random access read view
     * @throws IOException if something went wrong when creating the view for the RandomAccessRead
     * 
     */
    RandomAccessReadView createView(long startPosition, long streamLength) throws IOException;
}
