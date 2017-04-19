/**
 * Description:
 * LittleEndianDataOutputStream.java Create on 2014-5-14
 *
 * @author shaowenzhang <shaowenzhang@tencent.com>
 * @version 1.0
 * Copyright (c) 2014 Tecent WXG AndroidTeam. All Rights Reserved.
 */
package com.mindprod.ledatastream;

/**
 * Copyright (C) 2007 The Guava Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/***
 * An implementation of {@link DataOutput} that uses little-endian byte ordering
 * for writing {@code char}, {@code short}, {@code int}, {@code float}, {@code
 * double}, and {@code long} values.
 * <p>
 * <b>Note:</b> This class intentionally violates the specification of its
 * supertype {@code DataOutput}, which explicitly requires big-endian byte
 * order.
 *
 * @author Chris Nokleberg
 * @author Keith Bottner
 * @since 8.0
 */

public class LittleEndianDataOutputStream extends FilterOutputStream
    implements DataOutput {

    /***
     * Creates a {@code LittleEndianDataOutputStream} that wraps the given stream.
     *
     * @param out the stream to delegate to
     */
    public LittleEndianDataOutputStream(OutputStream out) {
        super(new DataOutputStream(out));
    }

    /***
     * Returns a big-endian representation of {@code value} in an 8-element byte
     * array; equivalent to {@code ByteBuffer.allocate(8).putLong(value).array()}.
     * For example, the input value {@code 0x1213141516171819L} would yield the
     * byte array {@code {0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19}}.
     * @param value long
     * @return byte array
     */
    public static byte[] toByteArray(long value) {
        // Note that this code needs to stay compatible with GWT, which has known
        // bugs when narrowing byte casts of long values occur.
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xffL);
            value >>= 8;
        }
        return result;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        // Override slow FilterOutputStream impl
        out.write(b, off, len);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        ((DataOutputStream) out).writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
        ((DataOutputStream) out).writeByte(v);
    }

    /***
     * @deprecated The semantics of {@code writeBytes(String s)} are considered
     *             dangerous. Please use {@link #writeUTF(String s)},
     *             {@link #writeChars(String s)} or another write method instead.
     */
    @Deprecated
    @Override
    public void writeBytes(String s) throws IOException {
        ((DataOutputStream) out).writeBytes(s);
    }

    /***
     * Writes a char as specified by {@link DataOutputStream#writeChar(int)},
     * except using little-endian byte order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void writeChar(int v) throws IOException {
        writeShort(v);
    }

    /***
     * Writes a {@code String} as specified by
     * {@link DataOutputStream#writeChars(String)}, except each character is
     * written using little-endian byte order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void writeChars(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            writeChar(s.charAt(i));
        }
    }

    /***
     * Writes a {@code double} as specified by
     * {@link DataOutputStream#writeDouble(double)}, except using little-endian
     * byte order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    /***
     * Writes a {@code float} as specified by
     * {@link DataOutputStream#writeFloat(float)}, except using little-endian byte
     * order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    /***
     * Writes an {@code int} as specified by
     * {@link DataOutputStream#writeInt(int)}, except using little-endian byte
     * order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void writeInt(int v) throws IOException {
        out.write(0xFF & v);
        out.write(0xFF & (v >> 8));
        out.write(0xFF & (v >> 16));
        out.write(0xFF & (v >> 24));
    }

    /***
     * Writes a {@code long} as specified by
     * {@link DataOutputStream#writeLong(long)}, except using little-endian byte
     * order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void writeLong(long v) throws IOException {
        byte[] bytes = toByteArray(Long.reverseBytes(v));
        write(bytes, 0, bytes.length);
    }

    /***
     * Writes a {@code short} as specified by
     * {@link DataOutputStream#writeShort(int)}, except using little-endian byte
     * order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void writeShort(int v) throws IOException {
        out.write(0xFF & v);
        out.write(0xFF & (v >> 8));
    }

    @Override
    public void writeUTF(String str) throws IOException {
        ((DataOutputStream) out).writeUTF(str);
    }
}