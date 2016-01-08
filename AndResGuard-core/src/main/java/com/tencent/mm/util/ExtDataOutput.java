
package com.tencent.mm.util;

import java.io.DataOutput;
import java.io.IOException;

public class ExtDataOutput extends DataOutputDelegate {


    public ExtDataOutput(DataOutput delegate) {
        super(delegate);
        // TODO Auto-generated constructor stub
    }

    public void writeIntArray(int[] array) throws IOException {
        int length = array.length;
        for (int i = 0; i < length; i++) {
            writeInt(array[i]);
        }
    }

    public void writeBytes(ExtDataInput in, int length) throws IOException {
        byte[] data = new byte[length];
        in.readFully(data);
        write(data);
    }

    public void writeCheckInt(int value, int expected) throws IOException {
        writeInt(value);
        if (value != expected) {
            throw new IOException(String.format(
                "Expected: 0x%08x, got: 0x%08x", expected, value));
        }
    }

    public void writeCheckChunkTypeInt(ExtDataInput reader, int expected, int possible) throws IOException {
        int value = reader.readInt();
        writeInt(value);
        if (value == possible) {
            writeCheckChunkTypeInt(reader, expected, -1);
        } else if (value != expected) {
            throw new IOException(String.format(
                "Expected: 0x%08x, got: 0x%08x", expected, value));
        }
    }

    public void writeCheckShort(short value, short expected) throws IOException {
        writeShort(value);
        if (value != expected) {
            throw new IOException(String.format(
                "Expected: 0x%08x, got: 0x%08x", expected, value));
        }
    }

    public void writeCheckByte(byte value, byte expected) throws IOException {
        writeByte(value);
        if (value != expected) {
            throw new IOException(String.format(
                "Expected: 0x%08x, got: 0x%08x", expected, value));
        }
    }

}
