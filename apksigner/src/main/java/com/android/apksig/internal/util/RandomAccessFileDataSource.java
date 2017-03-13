/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.apksig.internal.util;

import com.android.apksig.util.DataSink;
import com.android.apksig.util.DataSource;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * {@link DataSource} backed by a {@link RandomAccessFile}.
 */
public class RandomAccessFileDataSource implements DataSource {

    private static final int MAX_READ_CHUNK_SIZE = 65536;

    private final RandomAccessFile mFile;
    private final long mOffset;
    private final long mSize;

    /**
     * Constructs a new {@code RandomAccessFileDataSource} based on the data contained in the
     * specified the whole file. Changes to the contents of the file, including the size of the
     * file, will be visible in this data source.
     */
    public RandomAccessFileDataSource(RandomAccessFile file) {
        mFile = file;
        mOffset = 0;
        mSize = -1;
    }

    /**
     * Constructs a new {@code RandomAccessFileDataSource} based on the data contained in the
     * specified region of the provided file. Changes to the contents of the file will be visible in
     * this data source.
     */
    public RandomAccessFileDataSource(RandomAccessFile file, long offset, long size) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset: " + size);
        }
        if (size < 0) {
            throw new IllegalArgumentException("size: " + size);
        }
        mFile = file;
        mOffset = offset;
        mSize = size;
    }

    @Override
    public long size() {
        if (mSize == -1) {
            try {
                return mFile.length();
            } catch (IOException e) {
                return 0;
            }
        } else {
            return mSize;
        }
    }

    @Override
    public RandomAccessFileDataSource slice(long offset, long size) {
        long sourceSize = size();
        checkChunkValid(offset, size, sourceSize);
        if ((offset == 0) && (size == sourceSize)) {
            return this;
        }

        return new RandomAccessFileDataSource(mFile, mOffset + offset, size);
    }

    @Override
    public void feed(long offset, long size, DataSink sink) throws IOException {
        long sourceSize = size();
        checkChunkValid(offset, size, sourceSize);
        if (size == 0) {
            return;
        }

        long chunkOffsetInFile = mOffset + offset;
        long remaining = size;
        byte[] buf = new byte[(int) Math.min(remaining, MAX_READ_CHUNK_SIZE)];
        while (remaining > 0) {
            int chunkSize = (int) Math.min(remaining, buf.length);
            synchronized (mFile) {
                mFile.seek(chunkOffsetInFile);
                mFile.readFully(buf, 0, chunkSize);
            }
            sink.consume(buf, 0, chunkSize);
            chunkOffsetInFile += chunkSize;
            remaining -= chunkSize;
        }
    }

    @Override
    public void copyTo(long offset, int size, ByteBuffer dest) throws IOException {
        long sourceSize = size();
        checkChunkValid(offset, size, sourceSize);
        if (size == 0) {
            return;
        }

        long offsetInFile = mOffset + offset;
        int remaining = size;
        int prevLimit = dest.limit();
        try {
            dest.limit(dest.position() + size);
            FileChannel fileChannel = mFile.getChannel();
            while (remaining > 0) {
                int chunkSize;
                synchronized (mFile) {
                    fileChannel.position(offsetInFile);
                    chunkSize = fileChannel.read(dest);
                }
                offsetInFile += chunkSize;
                remaining -= chunkSize;
            }
        } finally {
            dest.limit(prevLimit);
        }
    }

    @Override
    public ByteBuffer getByteBuffer(long offset, int size) throws IOException {
        ByteBuffer result = ByteBuffer.allocate(size);
        copyTo(offset, size, result);
        result.flip();
        return result;
    }

    private static void checkChunkValid(long offset, long size, long sourceSize) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset: " + offset);
        }
        if (size < 0) {
            throw new IllegalArgumentException("size: " + size);
        }
        if (offset > sourceSize) {
            throw new IllegalArgumentException(
                    "offset (" + offset + ") > source size (" + sourceSize + ")");
        }
        long endOffset = offset + size;
        if (endOffset < offset) {
            throw new IllegalArgumentException(
                    "offset (" + offset + ") + size (" + size + ") overflow");
        }
        if (endOffset > sourceSize) {
            throw new IllegalArgumentException(
                    "offset (" + offset + ") + size (" + size
                            + ") > source size (" + sourceSize  +")");
        }
    }
}
