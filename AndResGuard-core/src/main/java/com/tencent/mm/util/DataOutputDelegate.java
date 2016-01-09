package com.tencent.mm.util;

import java.io.DataOutput;
import java.io.IOException;

public class DataOutputDelegate implements DataOutput {
    protected final DataOutput mDelegate;

    public DataOutputDelegate(DataOutput delegate) {
        this.mDelegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.write(b);

    }

    @Override
    public void write(byte[] b) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.write(b, off, len);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeByte(v);

    }

    @Override
    public void writeShort(int v) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeShort(v);
    }

    @Override
    public void writeChar(int v) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeChar(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeInt(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeLong(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeFloat(v);
    }

    @Override
    public void writeDouble(double v) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeDouble(v);
    }

    @Override
    public void writeBytes(String s) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeBytes(s);
    }

    @Override
    public void writeChars(String s) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeChars(s);

    }

    @Override
    public void writeUTF(String s) throws IOException {
        // TODO Auto-generated method stub
        this.mDelegate.writeUTF(s);
    }
}
