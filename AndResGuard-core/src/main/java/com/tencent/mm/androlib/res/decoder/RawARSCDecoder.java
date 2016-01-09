package com.tencent.mm.androlib.res.decoder;

import com.mindprod.ledatastream.LEDataInputStream;
import com.tencent.mm.androlib.AndrolibException;
import com.tencent.mm.androlib.res.data.ResPackage;
import com.tencent.mm.androlib.res.data.ResType;
import com.tencent.mm.util.ExtDataInput;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 其实应该是原来有，并且在白名单里面的才去掉！现在没有判断是否在白名单中
 *
 * @author shwenzhang
 */
public class RawARSCDecoder {
    private final static short  ENTRY_FLAG_COMPLEX = 0x0001;
    private static final Logger LOGGER             = Logger.getLogger(ARSCDecoder.class.getName());
    private static final int    KNOWN_CONFIG_BYTES = 38;

    private static HashMap<Integer, Set<String>> mExistTypeNames;

    private ExtDataInput mIn;
    private Header       mHeader;
    private StringBlock  mTypeNames;
    private StringBlock  mSpecNames;
    private ResPackage   mPkg;
    private ResType      mType;
    private int mCurTypeID = -1;
    private ResPackage[] mPkgs;
    private int          mResId;


    private RawARSCDecoder(InputStream arscStream) throws AndrolibException, IOException {
        mIn = new ExtDataInput(new LEDataInputStream(arscStream));
        mExistTypeNames = new HashMap<Integer, Set<String>>();
    }

    public static ResPackage[] decode(InputStream arscStream
    )
        throws AndrolibException {
        try {
            RawARSCDecoder decoder = new RawARSCDecoder(arscStream);
            System.out.printf("parse to get the exist names in the resouces.arsc first\n");

            ResPackage[] pkgs = decoder.readTable();

            return pkgs;
        } catch (IOException ex) {
            throw new AndrolibException("Could not decode arsc file", ex);
        }
    }

    public static Set<String> getExistTypeSpecNameStrings(int type) {
        return mExistTypeNames.get(type);
    }

    private ResPackage[] readTable() throws IOException, AndrolibException {
        nextChunkCheckType(Header.TYPE_TABLE);
        int packageCount = mIn.readInt();
        StringBlock.read(mIn);
        ResPackage[] packages = new ResPackage[packageCount];
        nextChunk();
        for (int i = 0; i < packageCount; i++) {
            packages[i] = readPackage();
        }
        return packages;
    }

    private ResPackage readPackage() throws IOException, AndrolibException {
        checkChunkType(Header.TYPE_PACKAGE);
        int id = (byte) mIn.readInt();
        String name = mIn.readNulEndedString(128, true);
        //add log
        /* typeNameStrings */
        mIn.skipInt();
        /* typeNameCount */
        mIn.skipInt();
        /* specNameStrings */
        mIn.skipInt();
        /* specNameCount */
        mIn.skipInt();
        mTypeNames = StringBlock.read(mIn);
        mSpecNames = StringBlock.read(mIn);
        mResId = id << 24;
        mPkg = new ResPackage(id, name);
        nextChunk();
        while (mHeader.type == Header.TYPE_TYPE) {
            readType();
        }

        return mPkg;
    }

    private void readType() throws AndrolibException, IOException {
        checkChunkType(Header.TYPE_TYPE);
        byte id = mIn.readByte();
        mIn.skipBytes(3);
        int entryCount = mIn.readInt();
        mCurTypeID = id;
        //对，这里是用来描述差异性的！！！
        mIn.skipBytes(entryCount * 4);
        mResId = (0xff000000 & mResId) | id << 16;
        mType = new ResType(mTypeNames.getString(id - 1), mPkg);
        while (nextChunk().type == Header.TYPE_CONFIG) {
            readConfig();
        }
    }

    private void readConfig() throws IOException, AndrolibException {
        checkChunkType(Header.TYPE_CONFIG);
        mIn.skipInt();
        int entryCount = mIn.readInt();
        int entriesStart = mIn.readInt();
        readConfigFlags();
        int[] entryOffsets = mIn.readIntArray(entryCount);
        for (int i = 0; i < entryOffsets.length; i++) {
            if (entryOffsets[i] != -1) {
                mResId = (mResId & 0xffff0000) | i;

                readEntry();
            }
        }
    }

    /**
     * 需要防止由于某些非常恶心的白名单，导致出现重复id
     *
     * @throws IOException
     * @throws AndrolibException
     */
    private void readEntry() throws IOException, AndrolibException {
        /* size */
        mIn.skipBytes(2);
        short flags = mIn.readShort();
        int specNamesId = mIn.readInt();
        putTypeSpecNameStrings(mCurTypeID, mSpecNames.getString(specNamesId));
        boolean readDirect = false;
        if ((flags & ENTRY_FLAG_COMPLEX) == 0) {
            readDirect = true;
            readValue(readDirect, specNamesId);
        } else {
            readDirect = false;
            readComplexEntry(readDirect, specNamesId);
        }
    }

    private void readComplexEntry(boolean flags, int specNamesId) throws IOException,
        AndrolibException {
        int parent = mIn.readInt();
        int count = mIn.readInt();
        for (int i = 0; i < count; i++) {
            mIn.readInt();
            readValue(flags, specNamesId);
        }
    }

    private void readValue(boolean flags, int specNamesId) throws IOException, AndrolibException {
        /* size */
        mIn.skipCheckShort((short) 8);
        /* zero */
        mIn.skipCheckByte((byte) 0);
        byte type = mIn.readByte();
        int data = mIn.readInt();
    }

    private void readConfigFlags() throws IOException,
        AndrolibException {
        int size = mIn.readInt();
        if (size < 28) {
            throw new AndrolibException("Config size < 28");
        }

        boolean isInvalid = false;
        short mcc = mIn.readShort();
        short mnc = mIn.readShort();
        char[] language = new char[]{(char) mIn.readByte(), (char) mIn.readByte()};
        char[] country = new char[]{(char) mIn.readByte(), (char) mIn.readByte()};
        byte orientation = mIn.readByte();
        byte touchscreen = mIn.readByte();
        int density = mIn.readUnsignedShort();
        byte keyboard = mIn.readByte();
        byte navigation = mIn.readByte();
        byte inputFlags = mIn.readByte();
        /* inputPad0 */
        mIn.skipBytes(1);

        short screenWidth = mIn.readShort();
        short screenHeight = mIn.readShort();

        short sdkVersion = mIn.readShort();
        /* minorVersion, now must always be 0 */
        mIn.skipBytes(2);

        byte screenLayout = 0;
        byte uiMode = 0;
        short smallestScreenWidthDp = 0;
        if (size >= 32) {
            screenLayout = mIn.readByte();
            uiMode = mIn.readByte();
            smallestScreenWidthDp = mIn.readShort();
        }

        short screenWidthDp = 0;
        short screenHeightDp = 0;
        if (size >= 36) {
            screenWidthDp = mIn.readShort();
            screenHeightDp = mIn.readShort();
        }

        short layoutDirection = 0;
        if (size >= 38) {
            layoutDirection = mIn.readShort();
        }

        int exceedingSize = size - KNOWN_CONFIG_BYTES;
        if (exceedingSize > 0) {
            byte[] buf = new byte[exceedingSize];
            mIn.readFully(buf);
            BigInteger exceedingBI = new BigInteger(1, buf);

            if (exceedingBI.equals(BigInteger.ZERO)) {
                LOGGER.fine(String
                    .format("Config flags size > %d, but exceeding bytes are all zero, so it should be ok.",
                        KNOWN_CONFIG_BYTES));
            } else {
                LOGGER.warning(String.format("Config flags size > %d. Exceeding bytes: 0x%X.",
                    KNOWN_CONFIG_BYTES, exceedingBI));
                isInvalid = true;
            }
        }
    }

    private Header nextChunk() throws IOException {
        return mHeader = Header.read(mIn);
    }

    private void checkChunkType(int expectedType) throws AndrolibException {
        if (mHeader.type != expectedType) {
            throw new AndrolibException(String.format(
                "Invalid chunk type: expected=0x%08x, got=0x%08x",
                expectedType, mHeader.type));
        }
    }

    private void nextChunkCheckType(int expectedType) throws IOException,
        AndrolibException {
        nextChunk();
        checkChunkType(expectedType);
    }

    private void putTypeSpecNameStrings(int type, String name) {
        Set<String> names = mExistTypeNames.get(type);
        if (names == null) {
            names = new HashSet<String>();
        }
        names.add(name);
        mExistTypeNames.put(type, names);
    }

    public static class Header {
        public final static short TYPE_NONE = -1, TYPE_TABLE = 0x0002,
            TYPE_PACKAGE                    = 0x0200, TYPE_TYPE = 0x0202,
            TYPE_CONFIG                     = 0x0201;
        public final short type;
        public final int   chunkSize;

        public Header(short type, int size) {
            this.type = type;
            this.chunkSize = size;
        }

        public static Header read(ExtDataInput in) throws IOException {
            short type;
            try {
                type = in.readShort();
            } catch (EOFException ex) {
                return new Header(TYPE_NONE, 0);
            }
            in.skipBytes(2);

            return new Header(type, in.readInt());
        }
    }

    public static class FlagsOffset {
        public final int offset;
        public final int count;

        public FlagsOffset(int offset, int count) {
            this.offset = offset;
            this.count = count;
        }
    }
}