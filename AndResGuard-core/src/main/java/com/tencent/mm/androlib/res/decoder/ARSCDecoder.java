/**
 * Copyright 2014 Ryszard Wiśniewski <brut.alll@gmail.com>
 * Copyright 2016 sim sun <sunsj1231@gmail.com>
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

package com.tencent.mm.androlib.res.decoder;

import com.mindprod.ledatastream.LEDataInputStream;
import com.mindprod.ledatastream.LEDataOutputStream;
import com.tencent.mm.androlib.AndrolibException;
import com.tencent.mm.androlib.ApkDecoder;
import com.tencent.mm.androlib.res.data.ResPackage;
import com.tencent.mm.androlib.res.data.ResType;
import com.tencent.mm.androlib.res.util.StringUtil;
import com.tencent.mm.resourceproguard.Configuration;
import com.tencent.mm.util.ExtDataInput;
import com.tencent.mm.util.ExtDataOutput;
import com.tencent.mm.util.FileOperation;
import com.tencent.mm.util.Md5Util;
import com.tencent.mm.util.TypedValue;
import com.tencent.mm.util.Utils;

import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ARSCDecoder {

  private final static boolean DEBUG = false;

  private final static short ENTRY_FLAG_COMPLEX = 0x0001;
  private static final Logger LOGGER = Logger.getLogger(ARSCDecoder.class.getName());
  private static final int KNOWN_CONFIG_BYTES = 56;

  public static Map<Integer, String> mTableStringsResguard = new LinkedHashMap<>();
  public static int mMergeDuplicatedResCount = 0;
  private final Map<String, String> mOldFileName;
  private final Map<String, Integer> mCurSpecNameToPos;
  private final HashSet<String> mShouldResguardTypeSet;
  private final ApkDecoder mApkDecoder;
  private ExtDataInput mIn;
  private ExtDataOutput mOut;
  private Header mHeader;
  private StringBlock mTableStrings;
  private StringBlock mTypeNames;
  private StringBlock mSpecNames;
  private ResPackage mPkg;
  private ResType mType;
  private ResPackage[] mPkgs;
  private int[] mPkgsLenghtChange;
  private int mTableLenghtChange = 0;
  private int mResId;
  private int mCurrTypeID = -1;
  private int mCurEntryID = -1;
  private int mCurPackageID = -1;
  private long mMergeDuplicatedResTotalSize = 0L;
  private ResguardStringBuilder mResguardBuilder;
  private boolean mShouldResguardForType = false;
  private Writer mMappingWriter;
  private Writer mMergeDuplicatedResMappingWriter;
  private List<MergeDuplicatedResInfo> mMergeDuplicatedResInfos = new ArrayList<>();

  private ARSCDecoder(InputStream arscStream, ApkDecoder decoder) throws AndrolibException, IOException {
    mOldFileName = new LinkedHashMap<>();
    mCurSpecNameToPos = new LinkedHashMap<>();
    mShouldResguardTypeSet = new HashSet<>();
    mIn = new ExtDataInput(new LEDataInputStream(arscStream));
    mApkDecoder = decoder;
    proguardFileName();
  }

  private ARSCDecoder(InputStream arscStream, ApkDecoder decoder, ResPackage[] pkgs) throws FileNotFoundException {
    mOldFileName = new LinkedHashMap<>();
    mCurSpecNameToPos = new LinkedHashMap<>();
    mShouldResguardTypeSet = new HashSet<>();
    mApkDecoder = decoder;
    mIn = new ExtDataInput(new LEDataInputStream(arscStream));
    mOut = new ExtDataOutput(new LEDataOutputStream(new FileOutputStream(mApkDecoder.getOutTempARSCFile(), false)));
    mPkgs = pkgs;
    mPkgsLenghtChange = new int[pkgs.length];
  }

  public static ResPackage[] decode(InputStream arscStream, ApkDecoder apkDecoder) throws AndrolibException {
    try {
      ARSCDecoder decoder = new ARSCDecoder(arscStream, apkDecoder);
      ResPackage[] pkgs = decoder.readTable();
      return pkgs;
    } catch (IOException ex) {
      throw new AndrolibException("Could not decode arsc file", ex);
    }
  }

  public static void write(InputStream arscStream, ApkDecoder decoder, ResPackage[] pkgs) throws AndrolibException {
    try {
      ARSCDecoder writer = new ARSCDecoder(arscStream, decoder, pkgs);
      writer.writeTable();
    } catch (IOException ex) {
      throw new AndrolibException("Could not decode arsc file", ex);
    }
  }

  private void proguardFileName() throws IOException, AndrolibException {
    mMappingWriter = new BufferedWriter(new FileWriter(mApkDecoder.getResMappingFile(), false));
    mMergeDuplicatedResMappingWriter = new BufferedWriter(new FileWriter(mApkDecoder.getMergeDuplicatedResMappingFile(), false));
    mMergeDuplicatedResMappingWriter.write("res filter path mapping:\n");
    mMergeDuplicatedResMappingWriter.flush();

    mResguardBuilder = new ResguardStringBuilder();
    mResguardBuilder.reset(null);

    final Configuration config = mApkDecoder.getConfig();

    File rawResFile = mApkDecoder.getRawResFile();

    File[] resFiles = rawResFile.listFiles();

    // 需要看看哪些类型是要混淆文件路径的
    for (File resFile : resFiles) {
      String raw = resFile.getName();
      if (raw.contains("-")) {
        raw = raw.substring(0, raw.indexOf("-"));
      }
      mShouldResguardTypeSet.add(raw);
    }

    if (!config.mKeepRoot) {
      // 需要保持之前的命名方式
      if (config.mUseKeepMapping) {
        HashMap<String, String> fileMapping = config.mOldFileMapping;
        List<String> keepFileNames = new ArrayList<>();
        // 这里面为了兼容以前，也需要用以前的文件名前缀，即res混淆成什么
        String resRoot = TypedValue.RES_FILE_PATH;
        for (String name : fileMapping.values()) {
          int dot = name.indexOf("/");
          if (dot == -1) {
            throw new IOException(String.format("the old mapping res file path should be like r/a, yours %s\n", name));
          }
          resRoot = name.substring(0, dot);
          keepFileNames.add(name.substring(dot + 1));
        }
        // 去掉所有之前保留的命名，为了简单操作，mapping里面有的都去掉
        mResguardBuilder.removeStrings(keepFileNames);

        for (File resFile : resFiles) {
          String raw = "res" + "/" + resFile.getName();
          if (fileMapping.containsKey(raw)) {
            mOldFileName.put(raw, fileMapping.get(raw));
          } else {
            mOldFileName.put(raw, resRoot + "/" + mResguardBuilder.getReplaceString());
          }
        }
      } else {
        for (int i = 0; i < resFiles.length; i++) {
          // 这里也要用linux的分隔符,如果普通的话，就是r
          mOldFileName.put("res" + "/" + resFiles[i].getName(),
             TypedValue.RES_FILE_PATH + "/" + mResguardBuilder.getReplaceString()
          );
        }
      }
      generalFileResMapping();
    }

    Utils.cleanDir(mApkDecoder.getOutResFile());
  }

  private ResPackage[] readTable() throws IOException, AndrolibException {
    nextChunkCheckType(Header.TYPE_TABLE);
    int packageCount = mIn.readInt();
    mTableStrings = StringBlock.read(mIn);
    ResPackage[] packages = new ResPackage[packageCount];
    nextChunk();
    for (int i = 0; i < packageCount; i++) {
      packages[i] = readPackage();
    }
    mMappingWriter.close();
    System.out.printf("resources mapping file %s done\n", mApkDecoder.getResMappingFile().getAbsolutePath());
    generalFilterEnd(mMergeDuplicatedResCount, mMergeDuplicatedResTotalSize);
    mMergeDuplicatedResMappingWriter.close();
    System.out.printf("resources filter mapping file %s done\n", mApkDecoder.getMergeDuplicatedResMappingFile().getAbsolutePath());
    return packages;
  }

  private void writeTable() throws IOException, AndrolibException {
    System.out.printf("writing new resources.arsc \n");
    mTableLenghtChange = 0;
    writeNextChunkCheck(Header.TYPE_TABLE, 0);
    int packageCount = mIn.readInt();
    mOut.writeInt(packageCount);

    mTableLenghtChange += StringBlock.writeTableNameStringBlock(mIn, mOut, mTableStringsResguard);
    writeNextChunk(0);
    if (packageCount != mPkgs.length) {
      throw new AndrolibException(String.format("writeTable package count is different before %d, now %d",
         mPkgs.length,
         packageCount
      ));
    }
    for (int i = 0; i < packageCount; i++) {
      mCurPackageID = i;
      writePackage();
    }
    // 最后需要把整个的size重写回去
    reWriteTable();
  }

  private void generalFileResMapping() throws IOException {
    mMappingWriter.write("res path mapping:\n");
    for (String raw : mOldFileName.keySet()) {
      mMappingWriter.write("    " + raw + " -> " + mOldFileName.get(raw));
      mMappingWriter.write("\n");
    }
    mMappingWriter.write("\n\n");
    mMappingWriter.write("res id mapping:\n");
    mMappingWriter.flush();
  }

  private void generalResIDMapping(
     String packageName, String typename, String specName, String replace) throws IOException {
    mMappingWriter.write("    "
       + packageName
       + ".R."
       + typename
       + "."
       + specName
       + " -> "
       + packageName
       + ".R."
       + typename
       + "."
       + replace);
    mMappingWriter.write("\n");
    mMappingWriter.flush();
  }

  private void generalFilterResIDMapping(
     String originalFile, String original, String replaceFile, String replace, long fileLen) throws IOException {
    mMergeDuplicatedResMappingWriter.write("    "
       + originalFile
       + " : "
       + original
       + " -> "
       + replaceFile
       + " : "
       + replace
       + " (size:"
       + getNetFileSizeDescription(fileLen)
       + ")");
    mMergeDuplicatedResMappingWriter.write("\n");
    mMergeDuplicatedResMappingWriter.flush();
  }

  private void generalFilterEnd(int count, long totalSize) throws IOException {
    mMergeDuplicatedResMappingWriter.write(
       "removed: count(" + count
          + "), totalSize(" + getNetFileSizeDescription(totalSize) + ")");
    mMergeDuplicatedResMappingWriter.flush();
  }

  private static String getNetFileSizeDescription(long size) {
    StringBuilder bytes = new StringBuilder();
    DecimalFormat format = new DecimalFormat("###.0");
    if (size >= 1024 * 1024 * 1024) {
      double i = (size / (1024.0 * 1024.0 * 1024.0));
      bytes.append(format.format(i)).append("GB");
    } else if (size >= 1024 * 1024) {
      double i = (size / (1024.0 * 1024.0));
      bytes.append(format.format(i)).append("MB");
    } else if (size >= 1024) {
      double i = (size / (1024.0));
      bytes.append(format.format(i)).append("KB");
    } else {
      if (size <= 0) {
        bytes.append("0B");
      } else {
        bytes.append((int) size).append("B");
      }
    }
    return bytes.toString();
  }

  private void reWriteTable() throws AndrolibException, IOException {

    mIn = new ExtDataInput(new LEDataInputStream(new FileInputStream(mApkDecoder.getOutTempARSCFile())));
    mOut = new ExtDataOutput(new LEDataOutputStream(new FileOutputStream(mApkDecoder.getOutARSCFile(), false)));
    writeNextChunkCheck(Header.TYPE_TABLE, mTableLenghtChange);
    int packageCount = mIn.readInt();
    mOut.writeInt(packageCount);
    StringBlock.writeAll(mIn, mOut);

    for (int i = 0; i < packageCount; i++) {
      mCurPackageID = i;
      writeNextChunk(mPkgsLenghtChange[mCurPackageID]);
      mOut.writeBytes(mIn, mHeader.chunkSize - 8);
    }
    mApkDecoder.getOutTempARSCFile().delete();
  }

  private ResPackage readPackage() throws IOException, AndrolibException {
    checkChunkType(Header.TYPE_PACKAGE);
    int id = (byte) mIn.readInt();
    String name = mIn.readNullEndedString(128, true);
    System.out.printf("reading packagename %s\n", name);

    /* typeNameStrings */
    mIn.skipInt();
    /* typeNameCount */
    mIn.skipInt();
    /* specNameStrings */
    mIn.skipInt();
    /* specNameCount */
    mIn.skipInt();
    mCurrTypeID = -1;
    mTypeNames = StringBlock.read(mIn);
    mSpecNames = StringBlock.read(mIn);
    mResId = id << 24;

    mPkg = new ResPackage(id, name);
    // 系统包名不混淆
    if (mPkg.getName().equals("android")) {
      mPkg.setCanResguard(false);
    } else {
      mPkg.setCanResguard(true);
    }
    nextChunk();
    while (mHeader.type == Header.TYPE_LIBRARY) {
      readLibraryType();
    }
    while (mHeader.type == Header.TYPE_SPEC_TYPE) {
      readTableTypeSpec();
    }
    return mPkg;
  }

  private void writePackage() throws IOException, AndrolibException {
    checkChunkType(Header.TYPE_PACKAGE);
    int id = (byte) mIn.readInt();
    mOut.writeInt(id);
    mResId = id << 24;
    //char_16的，一共256byte
    mOut.writeBytes(mIn, 256);
    /* typeNameStrings */
    mOut.writeInt(mIn.readInt());
    /* typeNameCount */
    mOut.writeInt(mIn.readInt());
    /* specNameStrings */
    mOut.writeInt(mIn.readInt());
    /* specNameCount */
    mOut.writeInt(mIn.readInt());
    StringBlock.writeAll(mIn, mOut);

    if (mPkgs[mCurPackageID].isCanResguard()) {
      int specSizeChange = StringBlock.writeSpecNameStringBlock(mIn,
         mOut,
         mPkgs[mCurPackageID].getSpecNamesBlock(),
         mCurSpecNameToPos
      );
      mPkgsLenghtChange[mCurPackageID] += specSizeChange;
      mTableLenghtChange += specSizeChange;
    } else {
      StringBlock.writeAll(mIn, mOut);
    }
    writeNextChunk(0);
    while (mHeader.type == Header.TYPE_LIBRARY) {
      writeLibraryType();
    }
    while (mHeader.type == Header.TYPE_SPEC_TYPE) {
      writeTableTypeSpec();
    }
  }

  /**
   * 如果是保持mapping的话，需要去掉某部分已经用过的mapping
   */
  private void reduceFromOldMappingFile() {
    if (mPkg.isCanResguard()) {
      if (mApkDecoder.getConfig().mUseKeepMapping) {
        // 判断是否走keepmapping
        HashMap<String, HashMap<String, HashMap<String, String>>> resMapping = mApkDecoder.getConfig().mOldResMapping;
        String packName = mPkg.getName();
        if (resMapping.containsKey(packName)) {
          HashMap<String, HashMap<String, String>> typeMaps = resMapping.get(packName);
          String typeName = mType.getName();

          if (typeMaps.containsKey(typeName)) {
            HashMap<String, String> proguard = typeMaps.get(typeName);
            // 去掉所有之前保留的命名，为了简单操作，mapping里面有的都去掉
            mResguardBuilder.removeStrings(proguard.values());
          }
        }
      }
    }
  }

  private HashSet<Pattern> getWhiteList(String resType) {
    final String packName = mPkg.getName();
    if (mApkDecoder.getConfig().mWhiteList.containsKey(packName)) {
      if (mApkDecoder.getConfig().mUseWhiteList) {
        HashMap<String, HashSet<Pattern>> typeMaps = mApkDecoder.getConfig().mWhiteList.get(packName);
        return typeMaps.get(resType);
      }
    }
    return null;
  }

  private void readLibraryType() throws AndrolibException, IOException {
    checkChunkType(Header.TYPE_LIBRARY);
    int libraryCount = mIn.readInt();

    int packageId;
    String packageName;

    for (int i = 0; i < libraryCount; i++) {
      packageId = mIn.readInt();
      packageName = mIn.readNullEndedString(128, true);
      System.out.printf("Decoding Shared Library (%s), pkgId: %d\n", packageName, packageId);
    }

    while (nextChunk().type == Header.TYPE_TYPE) {
      readTableTypeSpec();
    }
  }

  private void readTableTypeSpec() throws AndrolibException, IOException {
    checkChunkType(Header.TYPE_SPEC_TYPE);
    byte id = mIn.readByte();
    mIn.skipBytes(3);
    int entryCount = mIn.readInt();
    mType = new ResType(mTypeNames.getString(id - 1), mPkg);
    if (DEBUG) {
      System.out.printf("[ReadTableType] type (%s) id: (%d) curr (%d)\n", mType, id, mCurrTypeID);
    }
    // first meet a type of resource
    if (mCurrTypeID != id) {
      mCurrTypeID = id;
      initResGuardBuild(mCurrTypeID);
    }
    // 是否混淆文件路径
    mShouldResguardForType = isToResguardFile(mTypeNames.getString(id - 1));

    // 对，这里是用来描述差异性的！！！
    mIn.skipBytes(entryCount * 4);
    mResId = (0xff000000 & mResId) | id << 16;

    while (nextChunk().type == Header.TYPE_TYPE) {
      readConfig();
    }
  }

  private void initResGuardBuild(int resTypeId) {
    // we need remove string from resguard candidate list if it exists in white list
    HashSet<Pattern> whiteListPatterns = getWhiteList(mType.getName());
    // init resguard builder
    mResguardBuilder.reset(whiteListPatterns);
    mResguardBuilder.removeStrings(RawARSCDecoder.getExistTypeSpecNameStrings(resTypeId));
    // 如果是保持mapping的话，需要去掉某部分已经用过的mapping
    reduceFromOldMappingFile();
  }

  private void writeLibraryType() throws AndrolibException, IOException {
    checkChunkType(Header.TYPE_LIBRARY);
    int libraryCount = mIn.readInt();
    mOut.writeInt(libraryCount);
    for (int i = 0; i < libraryCount; i++) {
      mOut.writeInt(mIn.readInt());/*packageId*/
      mOut.writeBytes(mIn, 256); /*packageName*/
    }
    writeNextChunk(0);
    while (mHeader.type == Header.TYPE_TYPE) {
      writeTableTypeSpec();
    }
  }

  private void writeTableTypeSpec() throws AndrolibException, IOException {
    checkChunkType(Header.TYPE_SPEC_TYPE);
    byte id = mIn.readByte();
    mOut.writeByte(id);
    mResId = (0xff000000 & mResId) | id << 16;
    mOut.writeBytes(mIn, 3);
    int entryCount = mIn.readInt();
    mOut.writeInt(entryCount);
    // 对，这里是用来描述差异性的！！！
    ///* flags */mIn.skipBytes(entryCount * 4);
    int[] entryOffsets = mIn.readIntArray(entryCount);
    mOut.writeIntArray(entryOffsets);

    while (writeNextChunk(0).type == Header.TYPE_TYPE) {
      writeConfig();
    }
  }

  private void readConfig() throws IOException, AndrolibException {
    checkChunkType(Header.TYPE_TYPE);
    /* typeId */
    mIn.skipInt();
    int entryCount = mIn.readInt();
    int entriesStart = mIn.readInt();
    readConfigFlags();
    int[] entryOffsets = mIn.readIntArray(entryCount);
    for (int i = 0; i < entryOffsets.length; i++) {
      mCurEntryID = i;
      if (entryOffsets[i] != -1) {
        mResId = (mResId & 0xffff0000) | i;
        readEntry();
      }
    }
  }

  private void writeConfig() throws IOException, AndrolibException {
    checkChunkType(Header.TYPE_TYPE);
    /* typeId */
    mOut.writeInt(mIn.readInt());
    /* entryCount */
    int entryCount = mIn.readInt();
    mOut.writeInt(entryCount);
    /* entriesStart */
    mOut.writeInt(mIn.readInt());

    writeConfigFlags();
    int[] entryOffsets = mIn.readIntArray(entryCount);
    mOut.writeIntArray(entryOffsets);

    for (int i = 0; i < entryOffsets.length; i++) {
      if (entryOffsets[i] != -1) {
        mResId = (mResId & 0xffff0000) | i;
        writeEntry();
      }
    }
  }

  private void readEntry() throws IOException, AndrolibException {
    mIn.skipBytes(2);
    short flags = mIn.readShort();
    int specNamesId = mIn.readInt();

    if (mPkg.isCanResguard()) {
      // 混淆过或者已经添加到白名单的都不需要再处理了
      if (!mResguardBuilder.isReplaced(mCurEntryID) && !mResguardBuilder.isInWhiteList(mCurEntryID)) {
        Configuration config = mApkDecoder.getConfig();
        boolean isWhiteList = false;
        if (config.mUseWhiteList) {
          isWhiteList = dealWithWhiteList(specNamesId, config);
        }

        if (!isWhiteList) {
          dealWithNonWhiteList(specNamesId, config);
        }
      }
    }

    if ((flags & ENTRY_FLAG_COMPLEX) == 0) {
      readValue(true, specNamesId);
    } else {
      readComplexEntry(false, specNamesId);
    }
  }

  /**
   * deal with whitelist
   *
   * @param specNamesId resource spec name id
   * @param config      {@Configuration} AndResGuard configuration
   * @return isWhiteList whether this resource is processed by whitelist
   */
  private boolean dealWithWhiteList(int specNamesId, Configuration config) throws AndrolibException {
    String packName = mPkg.getName();
    if (config.mWhiteList.containsKey(packName)) {
      HashMap<String, HashSet<Pattern>> typeMaps = config.mWhiteList.get(packName);
      String typeName = mType.getName();
      if (typeMaps.containsKey(typeName)) {
        String specName = mSpecNames.get(specNamesId).toString();
        HashSet<Pattern> patterns = typeMaps.get(typeName);
        for (Iterator<Pattern> it = patterns.iterator(); it.hasNext(); ) {
          Pattern p = it.next();
          if (p.matcher(specName).matches()) {
            if (DEBUG) {
              System.out.printf("[match] matcher %s ,typeName %s, specName :%s\n", p.pattern(), typeName, specName);
            }
            mPkg.putSpecNamesReplace(mResId, specName);
            mPkg.putSpecNamesblock(specName);
            mResguardBuilder.setInWhiteList(mCurEntryID);

            mType.putSpecResguardName(specName);
            return true;
          }
        }
      }
    }
    return false;
  }

  private void dealWithNonWhiteList(int specNamesId, Configuration config) throws AndrolibException, IOException {
    String replaceString = null;
    boolean keepMapping = false;
    if (config.mUseKeepMapping) {
      String packName = mPkg.getName();
      if (config.mOldResMapping.containsKey(packName)) {
        HashMap<String, HashMap<String, String>> typeMaps = config.mOldResMapping.get(packName);
        String typeName = mType.getName();
        if (typeMaps.containsKey(typeName)) {
          HashMap<String, String> nameMap = typeMaps.get(typeName);
          String specName = mSpecNames.get(specNamesId).toString();
          if (nameMap.containsKey(specName)) {
            keepMapping = true;
            replaceString = nameMap.get(specName);
          }
        }
      }
    }

    if (!keepMapping) {
      replaceString = mResguardBuilder.getReplaceString();
    }

    mResguardBuilder.setInReplaceList(mCurEntryID);
    if (replaceString == null) {
      throw new AndrolibException("readEntry replaceString == null");
    }
    generalResIDMapping(mPkg.getName(), mType.getName(), mSpecNames.get(specNamesId).toString(), replaceString);
    mPkg.putSpecNamesReplace(mResId, replaceString);
    mPkg.putSpecNamesblock(replaceString);
    mType.putSpecResguardName(replaceString);
  }

  private void writeEntry() throws IOException, AndrolibException {
    /* size */
    mOut.writeBytes(mIn, 2);
    short flags = mIn.readShort();
    mOut.writeShort(flags);
    int specNamesId = mIn.readInt();
    ResPackage pkg = mPkgs[mCurPackageID];
    if (pkg.isCanResguard()) {
      specNamesId = mCurSpecNameToPos.get(pkg.getSpecRepplace(mResId));
      if (specNamesId < 0) {
        throw new AndrolibException(String.format("writeEntry new specNamesId < 0 %d", specNamesId));
      }
    }
    mOut.writeInt(specNamesId);

    if ((flags & ENTRY_FLAG_COMPLEX) == 0) {
      writeValue();
    } else {
      writeComplexEntry();
    }
  }

  /**
   * @param flags whether read direct
   */
  private void readComplexEntry(boolean flags, int specNamesId) throws IOException, AndrolibException {
    int parent = mIn.readInt();
    int count = mIn.readInt();
    for (int i = 0; i < count; i++) {
      mIn.readInt();
      readValue(flags, specNamesId);
    }
  }

  private void writeComplexEntry() throws IOException, AndrolibException {
    mOut.writeInt(mIn.readInt());
    int count = mIn.readInt();
    mOut.writeInt(count);
    for (int i = 0; i < count; i++) {
      mOut.writeInt(mIn.readInt());
      writeValue();
    }
  }

  /**
   * @param flags whether read direct
   */
  private void readValue(boolean flags, int specNamesId) throws IOException, AndrolibException {
    /* size */
    mIn.skipCheckShort((short) 8);
    /* zero */
    mIn.skipCheckByte((byte) 0);
    byte type = mIn.readByte();
    int data = mIn.readInt();

    //这里面有几个限制，一对于string ,id, array我们是知道肯定不用改的，第二看要那个type是否对应有文件路径
    if (mPkg.isCanResguard()
       && flags
       && type == TypedValue.TYPE_STRING
       && mShouldResguardForType
       && mShouldResguardTypeSet.contains(mType.getName())) {
      if (mTableStringsResguard.get(data) == null) {
        String raw = mTableStrings.get(data).toString();
        if (StringUtil.isBlank(raw) || raw.equalsIgnoreCase("null")) return;

        String proguard = mPkg.getSpecRepplace(mResId);
        //这个要写死这个，因为resources.arsc里面就是用这个
        int secondSlash = raw.lastIndexOf("/");
        if (secondSlash == -1) {
          throw new AndrolibException(String.format("can not find \\ or raw string in res path = %s", raw));
        }

        String newFilePath = raw.substring(0, secondSlash);

        if (!mApkDecoder.getConfig().mKeepRoot) {
          newFilePath = mOldFileName.get(raw.substring(0, secondSlash));
        }
        if (newFilePath == null) {
          System.err.printf("can not found new res path, raw=%s\n", raw);
          return;
        }
        //同理这里不能用File.separator，因为resources.arsc里面就是用这个
        String result = newFilePath + "/" + proguard;
        int firstDot = raw.indexOf(".");
        if (firstDot != -1) {
          result += raw.substring(firstDot);
        }
        String compatibaleraw = new String(raw);
        String compatibaleresult = new String(result);

        //为了适配window要做一次转换
        if (!File.separator.contains("/")) {
          compatibaleresult = compatibaleresult.replace("/", File.separator);
          compatibaleraw = compatibaleraw.replace("/", File.separator);
        }

        File resRawFile = new File(mApkDecoder.getOutTempDir().getAbsolutePath() + File.separator + compatibaleraw);
        File resDestFile = new File(mApkDecoder.getOutDir().getAbsolutePath() + File.separator + compatibaleresult);

        MergeDuplicatedResInfo filterInfo = null;
        boolean mergeDuplicatedRes = mApkDecoder.getConfig().mMergeDuplicatedRes;
        if (mergeDuplicatedRes) {
          filterInfo = mergeDuplicated(resRawFile, resDestFile, compatibaleraw, result);
          if (filterInfo != null) {
            resDestFile = new File(filterInfo.filePath);
            result = filterInfo.fileName;
          }
        }

        //这里用的是linux的分隔符
        HashMap<String, Integer> compressData = mApkDecoder.getCompressData();
        if (compressData.containsKey(raw)) {
          compressData.put(result, compressData.get(raw));
        } else {
          System.err.printf("can not find the compress dataresFile=%s\n", raw);
        }

        if (!resRawFile.exists()) {
          System.err.printf("can not find res file, you delete it? path: resFile=%s\n", resRawFile.getAbsolutePath());
        } else {
          if (!mergeDuplicatedRes && resDestFile.exists()) {
            throw new AndrolibException(String.format("res dest file is already  found: destFile=%s",
               resDestFile.getAbsolutePath()
            ));
          }
          if (filterInfo == null) {
            FileOperation.copyFileUsingStream(resRawFile, resDestFile);
          }
          //already copied
          mApkDecoder.removeCopiedResFile(resRawFile.toPath());
          mTableStringsResguard.put(data, result);
        }
      }
    }
  }

  /**
   * resource filtering, filtering duplicate resources, reducing the volume of apk
   */
  private MergeDuplicatedResInfo mergeDuplicated(File resRawFile, File resDestFile, String compatibaleraw, String result) throws IOException {
    MergeDuplicatedResInfo filterInfo = null;
    for (MergeDuplicatedResInfo mergeDuplicatedResInfo : mMergeDuplicatedResInfos) {
      if (mergeDuplicatedResInfo.fileSize == resRawFile.length()) {
        if (mergeDuplicatedResInfo.md5 == null) {
          mergeDuplicatedResInfo.md5 = Md5Util.getMD5Str(new File(mergeDuplicatedResInfo.filePath));
        }
        String resRawFileMd5 = Md5Util.getMD5Str(resRawFile);
        if (resRawFileMd5 != null && resRawFileMd5.equals(mergeDuplicatedResInfo.md5)) {
          filterInfo = mergeDuplicatedResInfo;
          filterInfo.md5 = resRawFileMd5;
          break;
        }
      }
    }
    if (filterInfo != null) {
      generalFilterResIDMapping(compatibaleraw, result, filterInfo.originalName, filterInfo.fileName, resRawFile.length());
      mMergeDuplicatedResCount++;
      mMergeDuplicatedResTotalSize += resRawFile.length();
    } else {
      MergeDuplicatedResInfo info = new MergeDuplicatedResInfo.Builder()
         .setFileName(result)
         .setFilePath( resDestFile.getAbsolutePath())
         .setOriginalName(compatibaleraw)
         .setFileSize( resRawFile.length())
         .create();
      info.fileName = result;
      info.filePath = resDestFile.getAbsolutePath();
      info.originalName = compatibaleraw;
      info.fileSize = resRawFile.length();
      mMergeDuplicatedResInfos.add(info);
    }
    return filterInfo;
  }

  private void writeValue() throws IOException, AndrolibException {
    /* size */
    mOut.writeCheckShort(mIn.readShort(), (short) 8);
    /* zero */
    mOut.writeCheckByte(mIn.readByte(), (byte) 0);
    byte type = mIn.readByte();
    mOut.writeByte(type);
    int data = mIn.readInt();
    mOut.writeInt(data);
  }

  private void readConfigFlags() throws IOException, AndrolibException {
    int size = mIn.readInt();
    int read = 28;
    if (size < 28) {
      throw new AndrolibException("Config size < 28");
    }

    boolean isInvalid = false;

    short mcc = mIn.readShort();
    short mnc = mIn.readShort();

    char[] language = new char[] { (char) mIn.readByte(), (char) mIn.readByte() };
    char[] country = new char[] { (char) mIn.readByte(), (char) mIn.readByte() };

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
      read = 32;
    }

    short screenWidthDp = 0;
    short screenHeightDp = 0;
    if (size >= 36) {
      screenWidthDp = mIn.readShort();
      screenHeightDp = mIn.readShort();
      read = 36;
    }

    char[] localeScript = null;
    char[] localeVariant = null;
    if (size >= 48) {
      localeScript = readScriptOrVariantChar(4).toCharArray();
      localeVariant = readScriptOrVariantChar(8).toCharArray();
      read = 48;
    }

    byte screenLayout2 = 0;
    if (size >= 52) {
      screenLayout2 = mIn.readByte();
      mIn.skipBytes(3); // reserved padding
      read = 52;
    }

    if (size >= 56) {
      mIn.skipBytes(4);
      read = 56;
    }

    int exceedingSize = size - KNOWN_CONFIG_BYTES;
    if (exceedingSize > 0) {
      byte[] buf = new byte[exceedingSize];
      read += exceedingSize;
      mIn.readFully(buf);
      BigInteger exceedingBI = new BigInteger(1, buf);

      if (exceedingBI.equals(BigInteger.ZERO)) {
        LOGGER.fine(String.format("Config flags size > %d, but exceeding bytes are all zero, so it should be ok.",
           KNOWN_CONFIG_BYTES
        ));
      } else {
        LOGGER.warning(String.format("Config flags size > %d. Exceeding bytes: 0x%X.",
           KNOWN_CONFIG_BYTES,
           exceedingBI
        ));
        isInvalid = true;
      }
    }
  }

  private String readScriptOrVariantChar(int length) throws AndrolibException, IOException {
    StringBuilder string = new StringBuilder(16);

    while (length-- != 0) {
      short ch = mIn.readByte();
      if (ch == 0) {
        break;
      }
      string.append((char) ch);
    }
    mIn.skipBytes(length);

    return string.toString();
  }

  private void writeConfigFlags() throws IOException, AndrolibException {
    //总的有多大
    int size = mIn.readInt();
    if (size < 28) {
      throw new AndrolibException("Config size < 28");
    }
    mOut.writeInt(size);

    mOut.writeBytes(mIn, size - 4);
  }

  private Header nextChunk() throws IOException {
    return mHeader = Header.read(mIn);
  }

  private void checkChunkType(int expectedType) throws AndrolibException {
    if (mHeader.type != expectedType) {
      throw new AndrolibException(String.format("Invalid chunk type: expected=0x%08x, got=0x%08x",
         expectedType,
         mHeader.type
      ));
    }
  }

  private void nextChunkCheckType(int expectedType) throws IOException, AndrolibException {
    nextChunk();
    checkChunkType(expectedType);
  }

  private Header writeNextChunk(int diffSize) throws IOException, AndrolibException {
    mHeader = Header.readAndWriteHeader(mIn, mOut, diffSize);
    return mHeader;
  }

  private Header writeNextChunkCheck(int expectedType, int diffSize) throws IOException, AndrolibException {
    mHeader = Header.readAndWriteHeader(mIn, mOut, diffSize);
    if (mHeader.type != expectedType) {
      throw new AndrolibException(String.format("Invalid chunk type: expected=%d, got=%d", expectedType, mHeader.type));
    }
    return mHeader;
  }

  /**
   * 为了加速，不需要处理string,id,array，这几个是肯定不是的
   */
  private boolean isToResguardFile(String name) {
    return (!name.equals("string") && !name.equals("id") && !name.equals("array"));
  }

  public static class Header {
    public final static short TYPE_NONE = -1, TYPE_TABLE = 0x0002, TYPE_PACKAGE = 0x0200, TYPE_TYPE = 0x0201,
       TYPE_SPEC_TYPE = 0x0202, TYPE_LIBRARY = 0x0203;

    public final short type;
    public final int chunkSize;

    public Header(short type, int size) {
      this.type = type;
      this.chunkSize = size;
    }

    public static Header read(ExtDataInput in) throws IOException {
      short type;
      try {
        type = in.readShort();
        short count = in.readShort();
        int size = in.readInt();
        return new Header(type, size);
      } catch (EOFException ex) {
        return new Header(TYPE_NONE, 0);
      }
    }

    public static Header readAndWriteHeader(ExtDataInput in, ExtDataOutput out, int diffSize)
       throws IOException, AndrolibException {
      short type;
      int size;
      try {
        type = in.readShort();
        out.writeShort(type);
        short count = in.readShort();
        out.writeShort(count);
        size = in.readInt();
        size -= diffSize;
        if (size <= 0) {
          throw new AndrolibException(String.format("readAndWriteHeader size < 0: size=%d", size));
        }
        out.writeInt(size);
      } catch (EOFException ex) {
        return new Header(TYPE_NONE, 0);
      }
      return new Header(type, size);
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

  private static class MergeDuplicatedResInfo {
    private String fileName;
    private String filePath;
    private String originalName;
    private String md5;
    private long fileSize;

    private MergeDuplicatedResInfo(String fileName, String filePath, String originalName, String md5, long fileSize) {
      this.fileName = fileName;
      this.filePath = filePath;
      this.originalName = originalName;
      this.md5 = md5;
      this.fileSize = fileSize;
    }

    static class Builder {
      private String fileName;
      private String filePath;
      private String originalName;
      private String md5;
      private long fileSize;

      Builder setFileName(String fileName) {
        this.fileName = fileName;
        return this;
      }

      Builder setFilePath(String filePath) {
        this.filePath = filePath;
        return this;
      }

      Builder setFileSize(long fileSize) {
        this.fileSize = fileSize;
        return this;
      }

      public Builder setMd5(String md5) {
        this.md5 = md5;
        return this;
      }

      Builder setOriginalName(String originalName) {
        this.originalName = originalName;
        return this;
      }

      MergeDuplicatedResInfo create() {
        return new MergeDuplicatedResInfo(fileName, filePath, originalName, md5, fileSize);
      }
    }
  }

  private class ResguardStringBuilder {
    private final List<String> mReplaceStringBuffer;
    private final Set<Integer> mIsReplaced;
    private final Set<Integer> mIsWhiteList;
    private String[] mAToZ = {
       "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v",
       "w", "x", "y", "z"
    };
    private String[] mAToAll = {
       "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "_", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k",
       "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
    };
    /**
     * 在window上面有些关键字是不能作为文件名的
     * CON, PRN, AUX, CLOCK$, NUL
     * COM1, COM2, COM3, COM4, COM5, COM6, COM7, COM8, COM9
     * LPT1, LPT2, LPT3, LPT4, LPT5, LPT6, LPT7, LPT8, and LPT9.
     */
    private HashSet<String> mFileNameBlackList;

    public ResguardStringBuilder() {
      mFileNameBlackList = new HashSet<>();
      mFileNameBlackList.add("con");
      mFileNameBlackList.add("prn");
      mFileNameBlackList.add("aux");
      mFileNameBlackList.add("nul");
      mReplaceStringBuffer = new ArrayList<>();
      mIsReplaced = new HashSet<>();
      mIsWhiteList = new HashSet<>();
    }

    public void reset(HashSet<Pattern> blacklistPatterns) {
      mReplaceStringBuffer.clear();
      mIsReplaced.clear();
      mIsWhiteList.clear();

      for (int i = 0; i < mAToZ.length; i++) {
        String str = mAToZ[i];
        if (!Utils.match(str, blacklistPatterns)) {
          mReplaceStringBuffer.add(str);
        }
      }

      for (int i = 0; i < mAToZ.length; i++) {
        String first = mAToZ[i];
        for (int j = 0; j < mAToAll.length; j++) {
          String str = first + mAToAll[j];
          if (!Utils.match(str, blacklistPatterns)) {
            mReplaceStringBuffer.add(str);
          }
        }
      }

      for (int i = 0; i < mAToZ.length; i++) {
        String first = mAToZ[i];
        for (int j = 0; j < mAToAll.length; j++) {
          String second = mAToAll[j];
          for (int k = 0; k < mAToAll.length; k++) {
            String third = mAToAll[k];
            String str = first + second + third;
            if (!mFileNameBlackList.contains(str) && !Utils.match(str, blacklistPatterns)) {
              mReplaceStringBuffer.add(str);
            }
          }
        }
      }
    }

    // 对于某种类型用过的mapping，全部不能再用了
    public void removeStrings(Collection<String> collection) {
      if (collection == null) return;
      mReplaceStringBuffer.removeAll(collection);
    }

    public boolean isReplaced(int id) {
      return mIsReplaced.contains(id);
    }

    public boolean isInWhiteList(int id) {
      return mIsWhiteList.contains(id);
    }

    public void setInWhiteList(int id) {
      mIsWhiteList.add(id);
    }

    public void setInReplaceList(int id) {
      mIsReplaced.add(id);
    }

    public String getReplaceString() throws AndrolibException {
      if (mReplaceStringBuffer.isEmpty()) {
        throw new AndrolibException(String.format("now can only proguard less than 35594 in a single type\n"));
      }
      return mReplaceStringBuffer.remove(0);
    }
  }
}