

package com.tencent.mm.androlib.res.decoder;

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

import com.mindprod.ledatastream.LEDataInputStream;
import com.mindprod.ledatastream.LEDataOutputStream;
import com.tencent.mm.androlib.AndrolibException;
import com.tencent.mm.androlib.ApkDecoder;
import com.tencent.mm.androlib.res.data.ResPackage;
import com.tencent.mm.androlib.res.data.ResType;
import com.tencent.mm.resourceproguard.Main;
import com.tencent.mm.util.ExtDataInput;
import com.tencent.mm.util.ExtDataOutput;
import com.tencent.mm.util.FileOperation;
import com.tencent.mm.util.TypedValue;


/**
 * @author shwenzhang
 *
 */
public class ARSCDecoder {
	

	public static ResPackage[] decode(InputStream arscStream, ApkDecoder apkDecoder
			)
			throws AndrolibException {
		try {
			ARSCDecoder decoder = new ARSCDecoder(arscStream,  apkDecoder);
			ResPackage[] pkgs = decoder.readTable();

			return pkgs;
		} catch (IOException ex) {
			throw new AndrolibException("Could not decode arsc file", ex);
		}
	}
	
	public static void write(InputStream arscStream,
			ApkDecoder decoder, ResPackage[] pkgs)
			throws AndrolibException {
		try {
			ARSCDecoder writer = new ARSCDecoder(arscStream, decoder, pkgs);
			writer.writeTable();

		} catch (IOException ex) {
			throw new AndrolibException("Could not decode arsc file", ex);
		}
	}

	private ARSCDecoder(InputStream arscStream, ApkDecoder decoder) throws AndrolibException, IOException {
		
		mIn = new ExtDataInput(new LEDataInputStream(arscStream));
		mApkDecoder = decoder;
		proguardFileName();
		
		
	}
	
	private void proguardFileName() throws IOException, AndrolibException {
		mMappingWriter = new BufferedWriter(new FileWriter(mApkDecoder.getResMappingFile(),false));

		mProguardBuilder = new ProguardStringBuilder();
		mProguardBuilder.reset();
		
		File rawResFile = mApkDecoder.getRawResFile();

		File[] resFiles = rawResFile.listFiles();
		
		//需要看看哪些类型是要混淆文件路径的
		for (File resFile : resFiles) {
			String raw = resFile.getName();
//			System.out.printf("raw  %s\n",raw);
			if (raw.contains("-")) {
				raw = raw.substring(0, raw.indexOf("-"));
			}
//			System.out.printf("mShouldProguardTypeSet add %s\n",raw);
			mShouldProguardTypeSet.add(raw);
		}
		
		final Main client = mApkDecoder.getClient();
		//需要保持之前的命名方式
		if (client.isUseKeepMapping()) {
			HashMap<String, String> fileMapping = client.getOldFileMapping();
//    		System.out.printf("use file mapping %d\n", fileMapping.size());
    		List<String> keepFileNames = new ArrayList<String>();
    		//这里面为了兼容以前，也需要用以前的文件名前缀，即res混淆成什么
    		String resRoot = null;
    		for (String name : fileMapping.values()) {
    			int dot = name.indexOf("/");
    			if (dot == -1) {
    				throw new IOException(
							String.format(
									"the old mapping res file path should be like r/a, yours %s\n",
									name));
    			}
    			resRoot = name.substring(0, dot);
    			keepFileNames.add(name.substring(dot + 1));
//        		System.out.printf("resRoot %s, name %s\n", resRoot, name.substring(dot + 1));
    		}
			//去掉所有之前保留的命名，为了简单操作，mapping里面有的都去掉
			mProguardBuilder.removeStrings(keepFileNames);
			
			for (File resFile : resFiles) {
				String raw = "res"+"/" + resFile.getName();
				if (fileMapping.containsKey(raw)) {
					mOldFileName.put(raw, fileMapping.get(raw));
				} else {
            		System.out.printf("can not find the file mapping %s\n", raw);
            		mOldFileName.put(raw, resRoot + "/" + mProguardBuilder.getReplaceString());
				}
			}
		} else {
			for (int i = 0; i < resFiles.length; i++) {
				//这里也要用linux的分隔符,如果普通的话，就是r
				mOldFileName.put("res"+"/" + resFiles[i].getName(), TypedValue.RES_FILE_PATH + "/" + mProguardBuilder.getReplaceString());
			}
		}
		
		generalFileResMapping();
 		
 		File destResDir = mApkDecoder.getOutResFile();
		FileOperation.deleteDir(destResDir);
		destResDir.mkdir();
	}
	
	private ARSCDecoder(InputStream arscStream,
			ApkDecoder decoder, ResPackage[] pkgs) throws FileNotFoundException {
		mApkDecoder = decoder;
		
		mIn = new ExtDataInput(new LEDataInputStream(arscStream));
		
//		mOutTempARSCFile = new File(out.getAbsoluteFile().getAbsolutePath() + File.separator + "resources_temp.arsc");
//		mOutARSCFile  = new File(out.getAbsoluteFile().getAbsolutePath() + File.separator + "resources.arsc");
		mOut = new ExtDataOutput(new LEDataOutputStream(new FileOutputStream(mApkDecoder.getOutTempARSCFile(), false)));
		mPkgs = pkgs;
		
		mPkgsLenghtChange = new int[pkgs.length];

	}

	private ResPackage[] readTable() throws IOException, AndrolibException {
		nextChunkCheckType(Header.TYPE_TABLE);
		int packageCount = mIn.readInt();
		//add log
//		System.out.printf("packageCount %d\n",packageCount);
		mTableStrings = StringBlock.read(mIn);
		
//		mProguardBuilder = new ProguardStringBuilder();
//		mProguardBuilder.reset();

		//add log
//		int count = mTableStrings.getCount();
//		System.out.printf("mTableStrings size: %d\n",count);
//		for (int i = 0; i < count; i++) {
//			System.out.printf("mTableStrings %d: %s\n",i,mTableStrings.get(i));
//			mTableStringDone.put(i, false);
//		}
		ResPackage[] packages = new ResPackage[packageCount];

		nextChunk();
		for (int i = 0; i < packageCount; i++) {
			packages[i] = readPackage();
		}
		
		System.out.printf("resources mapping file %s done\n", mApkDecoder.getResMappingFile().getAbsolutePath());

		mMappingWriter.close();
		return packages;
	}
	
	
	private void writeTable() throws IOException, AndrolibException {
		System.out.printf("writing new resources.arsc \n");

		mTableLenghtChange = 0;

		writeNextChunkCheck(Header.TYPE_TABLE, 0);
		int packageCount = mIn.readInt();
		mOut.writeInt(packageCount);
		
		//add log
//		System.out.printf("packageCount %d\n",packageCount);
//		StringBlock.writeAll(mIn, mOut);
		mTableLenghtChange += StringBlock.writeTableNameStringBlock(mIn, mOut, mTableStringsProguard);
		writeNextChunk(0);
		if (packageCount != mPkgs.length) {
			throw new AndrolibException(String.format(
					"writeTable package count is different before %d, now %d", mPkgs.length, packageCount));
		}
		for (int i = 0; i < packageCount; i++) {
			mCurPackageID = i;
			writePackage();
		}
		
		//最后需要把整个的size重写回去
		reWriteTable();
	}
	
	private void generalFileResMapping() throws IOException {
		mMappingWriter.write("res path mapping:\n");
 		for(String raw : mOldFileName.keySet()) {
 			mMappingWriter.write("    "+raw + " -> "+mOldFileName.get(raw));
 			mMappingWriter.write("\n");
 		}
 		
		mMappingWriter.write("\n\n");
		mMappingWriter.write("res id mapping:\n");
		mMappingWriter.flush();
	}
	
	private void generalResIDMapping(String packagename, String typename, String specname, String replace) throws IOException {
		mMappingWriter.write("    " + packagename + ".R."+typename+"."+specname + " -> "+ packagename + ".R."+typename+"."+replace);
		mMappingWriter.write("\n");
 		
 		mMappingWriter.flush();
 		
	}
	
	private void reWriteTable() throws AndrolibException, IOException {
		
		mIn = new ExtDataInput(new LEDataInputStream(new FileInputStream(mApkDecoder.getOutTempARSCFile())));
		mOut = new ExtDataOutput(new LEDataOutputStream(new FileOutputStream(mApkDecoder.getOutARSCFile(), false)));
		
		writeNextChunkCheck(Header.TYPE_TABLE, mTableLenghtChange);
		System.out.printf("resources.arsc reduece: %fkb, time cost from begin: %fs\n", mTableLenghtChange / 1024.0, mApkDecoder.getClient().diffTimeFromBegin());

		int packageCount = mIn.readInt();
		mOut.writeInt(packageCount);
		//add log
		StringBlock.writeAll(mIn, mOut);
		
		for (int i = 0; i < packageCount; i++) {
			mCurPackageID = i;
			writeNextChunk(mPkgsLenghtChange[mCurPackageID]);
			
//			System.out.printf("reWriteTable mHeader.chunkSize %d\n",mHeader.chunkSize);
			mOut.writeBytes(mIn, mHeader.chunkSize - 8);
		}
		mApkDecoder.getOutTempARSCFile().delete();
		

	}

	private ResPackage readPackage() throws IOException, AndrolibException {
		checkChunkType(Header.TYPE_PACKAGE);
		int id = (byte) mIn.readInt();
		String name = mIn.readNulEndedString(128, true);
		//add log
		System.out.printf("reading packagename %s\n",name);
		
		/* typeNameStrings */mIn.skipInt();
		/* typeNameCount */mIn.skipInt();
		/* specNameStrings */mIn.skipInt();
		/* specNameCount */mIn.skipInt();
		
		mCurTypeID = -1;
		
		mTypeNames = StringBlock.read(mIn);
		//add log
//		int count = mTypeNames.getCount();
//		System.out.printf("mTypeNames size: %d\n",count);
//		for (int i = 0; i < count; i++) {
//			System.out.printf("mTypeNames %d: %s\n",i,mTypeNames.get(i));
//		}
		
		mSpecNames = StringBlock.read(mIn);
		
		
//		System.out.printf("readPackage id %d\n",id);

		mResId = id << 24;
//		System.out.printf("readPackage package id mResId %d\n",mResId);

		mPkg = new ResPackage(id, name);
		//系统包名不混淆
		if (mPkg.getName().equals("android")) {
			mPkg.setCanProguard(false);
		} else {
			mPkg.setCanProguard(true);
		}
		
		//add log
//		count = mSpecNames.getCount();
		
//		System.out.printf("mSpecNames size: %d\n",count);
//		if (mPkg.isCanProguard()) {
//			for (int i = 0; i < count; i++) {
//				System.out.printf("mSpecNames %d: %s\n",i,mSpecNames.get(i));
//				mPkg.putSpecNamesOldBlock(i, mSpecNames.get(i).toString());
	//
//			}
//		}
				
		nextChunk();
		while (mHeader.type == Header.TYPE_TYPE) {
			readType();
		}

		return mPkg;
	}
	
	
	private void writePackage() throws IOException, AndrolibException {
		checkChunkType(Header.TYPE_PACKAGE);
		int id = (byte) mIn.readInt();
		mOut.writeInt(id);
		
		mResId = id << 24;
//		System.out.printf("writePackage package id mResId %d\n",mResId);
		
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
		
//		StringBlock.writeAll(mIn, mOut);
		if (mPkgs[mCurPackageID].isCanProguard()) {
			int specSizeChange = StringBlock.writeSpecNameStringBlock(mIn, mOut, mPkgs[mCurPackageID].getSpecNamesBlock(), mCurSpecNameToPos);
			mPkgsLenghtChange[mCurPackageID] += specSizeChange;
			
			mTableLenghtChange += specSizeChange;
		} else {
			StringBlock.writeAll(mIn, mOut);
		}
		
		
				
		writeNextChunk(0);
		
		while (mHeader.type == Header.TYPE_TYPE) {
			writeType();
		}

	}
	
	/**
	 * 如果是保持mapping的话，需要去掉某部分已经用过的mapping
	 */
	private void reduceFromOldMappingFile() {
		if (mPkg.isCanProguard()) {
			Main client = mApkDecoder.getClient();
			if (client.isUseKeepMapping()) {
				// 判断是否走keepmapping
				HashMap<String, HashMap<String, HashMap<String, String>>> resMapping = client
						.getOldResMapping();
				String packName = mPkg.getName();
				if (resMapping.containsKey(packName)) {
//					System.out.printf("resMapping.containsKey(packName) %s\n",packName);

					HashMap<String, HashMap<String, String>> typeMaps = resMapping.get(packName);
					String typeName = mType.getName();

					if (typeMaps.containsKey(typeName)) {
//						System.out.printf("resMapping.containsKey(typeName) %s\n",typeName);
						HashMap<String, String> proguard = typeMaps.get(typeName);
						
						//去掉所有之前保留的命名，为了简单操作，mapping里面有的都去掉
						mProguardBuilder.removeStrings(proguard.values());
					}
				}
			}
		}
	}

	private void readType() throws AndrolibException, IOException {
		checkChunkType(Header.TYPE_TYPE);
		byte id = mIn.readByte();
		mIn.skipBytes(3);
		int entryCount = mIn.readInt();
		
		if (mCurTypeID != id) {
			mProguardBuilder.reset();
			mCurTypeID = id;
//			System.out.printf("mCurTypeID id %d\n",id);
//			System.out.printf("typename %s\n",mTypeNames.getString(mCurTypeID));
//			System.out.printf("before mProguardBuilder lenght %d\n",mProguardBuilder.lenght());

			Set<String> existNames = RawARSCDecoder.getExistTypeSpecNameStrings(mCurTypeID);
			mProguardBuilder.removeStrings(existNames);
//			System.out.printf("after mProguardBuilder lenght %d\n",mProguardBuilder.lenght());

		}
		//是否混淆文件路径
		mShouldProguardForType = isToProguardFile(mTypeNames.getString(id - 1));
		
		//对，这里是用来描述差异性的！！！
		/* flags */mIn.skipBytes(entryCount * 4);
//		int[] entryOffsets = mIn.readIntArray(entryCount);
		
		
//		System.out.printf("readType id %d\n",id);
		mResId = (0xff000000 & mResId) | id << 16;
//		System.out.printf("readType mResId %d, id %d, %s \n",mResId,id, mTypeNames.getString(id - 1));
		
		mType = new ResType(mTypeNames.getString(id - 1), mPkg);
//		System.out.printf("typename %s\n",mType.getName());

		//如果是保持mapping的话，需要去掉某部分已经用过的mapping
		reduceFromOldMappingFile();

		while (nextChunk().type == Header.TYPE_CONFIG) {
			readConfig();
		}

		
	}
	
	private void writeType() throws AndrolibException, IOException {
		checkChunkType(Header.TYPE_TYPE);

		byte id = mIn.readByte();
		mOut.writeByte(id);
		
		mResId = (0xff000000 & mResId) | id << 16;
//		System.out.printf("writeType mResId %d\n",mResId);

		mOut.writeBytes(mIn, 3);
		
		int entryCount = mIn.readInt();
		
		mOut.writeInt(entryCount);
		
		
		//对，这里是用来描述差异性的！！！
//		/* flags */mIn.skipBytes(entryCount * 4);
		int[] entryOffsets = mIn.readIntArray(entryCount);
		mOut.writeIntArray(entryOffsets);
		
		while (writeNextChunk(0).type == Header.TYPE_CONFIG) {
			writeConfig();
		}

	}

	private void readConfig() throws IOException, AndrolibException {
		checkChunkType(Header.TYPE_CONFIG);
		/* typeId */mIn.skipInt();
		int entryCount = mIn.readInt();
		int entriesStart = mIn.readInt();
//		/* entriesStart */ mIn.skipInt
		//add log
//		System.out.printf("readConfig  entryCount %d\n",entryCount);
		//add log
//		System.out.printf("readConfig  entriesStart %d\n",entriesStart);
		
		readConfigFlags();
		int[] entryOffsets = mIn.readIntArray(entryCount);
		
		

/*		mConfig = flags.isInvalid && !mKeepBroken ? null : mPkg
				.getOrCreateConfig(flags);*/

		for (int i = 0; i < entryOffsets.length; i++) {
//			System.out.printf("readConfig entryOffsets %d\n",entryOffsets[i]);
			mCurEntryID = i;
			if (entryOffsets[i] != -1) {
				mResId = (mResId & 0xffff0000) | i;
//				System.out.printf("readConfig mResId %d\n",mResId);

				readEntry();
			}
		}

//		return mConfig;
	}
	
	private void writeConfig() throws IOException, AndrolibException {
		checkChunkType(Header.TYPE_CONFIG);
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
//				System.out.printf("writeConfig mResId %d\n",mResId);
				writeEntry();
			}
		}

	}

	/**
	 * 需要防止由于某些非常恶心的白名单，导致出现重复id
	 * @throws IOException
	 * @throws AndrolibException
	 */
	private void readEntry() throws IOException, AndrolibException {
		/* size */mIn.skipBytes(2);
		short flags = mIn.readShort();
		int specNamesId = mIn.readInt(); 
		
		//add log
//		System.out.printf("readEntry specNamesId  %d: %s\n",specNamesId, mSpecNames.get(specNamesId));
		
		if (mPkg.isCanProguard()) {
			//混效过，或者已经添加到白名单的都不需要再处理了
			if (!mProguardBuilder.isReplaced(mCurEntryID) && !mProguardBuilder.isInWhiteList(mCurEntryID)) {
				Main client = mApkDecoder.getClient();
				boolean isWhiteList = false;
				if (client.isUseWhiteList()) {

					//判断是否走whitelist
					HashMap<String, HashMap<String, HashSet<Pattern>>> whiteList = client.getWhiteList();
					String packName = mPkg.getName();
					if (whiteList.containsKey(packName)) {
//						System.out.printf("whiteList.containsKey(packName) %s\n", packName);

						HashMap<String, HashSet<Pattern>> typeMaps = whiteList.get(packName);
						String typeName = mType.getName();
//						System.out.printf("whiteList.containsKey(typeName) %s\n", typeName);

						if (typeMaps.containsKey(typeName)) {
//							System.out.printf("whiteList.containsKey(typeName) %s\n", typeName);
							String specName = mSpecNames.get(specNamesId).toString();
							HashSet<Pattern> patterns = typeMaps.get(typeName);
							for (Iterator<Pattern> it = patterns.iterator(); it.hasNext();) {
								Pattern p = it.next();
								if (p.matcher(specName).matches()) {
									mPkg.putSpecNamesReplace(mResId, specName);
									mPkg.putSpecNamesblock(specName);
									mProguardBuilder.setInWhiteList(mCurEntryID, true);
									
									mType.putSpecProguardName(specName);
									isWhiteList = true;
//									System.out.printf("whitelist specName: %s\n",specName);

									break;
								}
							}
						}

					}
					
					
				} 
				
				String replaceString = null;
				
				if (!isWhiteList) {
					boolean keepMapping = false;
					if (client.isUseKeepMapping()) {
						HashMap<String, HashMap<String, HashMap<String, String>>> resMapping = client
								.getOldResMapping();
						String packName = mPkg.getName();
						if (resMapping.containsKey(packName)) {
//							System.out.printf("resMapping.containsKey(packName) %s\n",packName);

							HashMap<String, HashMap<String, String>> typeMaps = resMapping.get(packName);
							String typeName = mType.getName();

							if (typeMaps.containsKey(typeName)) {
//								System.out.printf("resMapping.containsKey(typeName) %s\n",typeName);
								//这里面的东东已经提前去掉，请放心使用
								HashMap<String, String> proguard = typeMaps.get(typeName);
								String specName = mSpecNames.get(specNamesId).toString();
								if (proguard.containsKey(specName)) {
									keepMapping = true;
									replaceString = proguard.get(specName);
//									System.out.printf("resMapping  specName %s, replaceString %s\n",specName, replaceString);

								}
								
							}
						}
					}
					
					if (!keepMapping) {
						replaceString = mProguardBuilder.getReplaceString();
//						System.out.printf("resMapping type %s, specname %s, replaceString %s\n", mType.getName(), mSpecNames.get(specNamesId).toString(),replaceString);
					}
					
						
					mProguardBuilder.setInReplaceList(mCurEntryID, true);
					if (replaceString == null) {
						throw new AndrolibException("readEntry replaceString == null");
					}
					generalResIDMapping(mPkg.getName(), mType.getName(), mSpecNames.get(specNamesId).toString(), replaceString);
					mPkg.putSpecNamesReplace(mResId, replaceString);
					mPkg.putSpecNamesblock(replaceString);
					mType.putSpecProguardName(replaceString);
					
//					System.out.printf("!!!readEntry id: %d, replace %s to %s\n",mResId, mSpecNames.getString(specNamesId), replaceString);
				}
			} 

		}

		//add log 这边除了0，1，还有一个2的！！！
//		System.out.printf("readEntry flags  %d\n",flags);
        // mappings.  It is followed by an array of ResTable_map structures.  
//        FLAG_COMPLEX = 0x0001,  
        // If set, this resource has been declared public, so libraries  
        // are allowed to reference it.  
//        FLAG_PUBLIC = 0x0002  
		boolean readDirect = false;
		if ((flags & ENTRY_FLAG_COMPLEX) == 0) {
			readDirect = true;
			readValue(readDirect, specNamesId);
		} else {
			readDirect = false;
			readComplexEntry(readDirect, specNamesId);
		}
		

//		if (mConfig == null) { 
//			return;
//		}
//				
//		ResID resId = new ResID(mResId);
//		ResResSpec spec;
//		if (mPkg.hasResSpec(resId)) {
//			spec = mPkg.getResSpec(resId);
//		} else {
//			spec = new ResResSpec(resId, mSpecNames.getString(specNamesId),
//					mPkg, mType);
//			mPkg.addResSpec(spec);
//			mType.addResSpec(spec);
//		}
//		ResResource res = new ResResource(mConfig, spec, value);
//		//add log
////		System.out.printf("readEntry  %s\n",spec.getFullName());
//		mConfig.addResource(res);
//		spec.addResource(res);
//		mPkg.addResource(res);
	}
	
	private void writeEntry() throws IOException, AndrolibException {
		/* size */

		mOut.writeBytes(mIn, 2);
		short flags = mIn.readShort();
		mOut.writeShort(flags);
		int specNamesId = mIn.readInt(); 
		ResPackage pkg = mPkgs[mCurPackageID];
		
		if (pkg.isCanProguard()) {
//			String oldSpecName = pkg.getSpecNamesOldBlock(specNamesId);

//			if (oldSpecName != null) {
				specNamesId = mCurSpecNameToPos.get(pkg.getSpecRepplace(mResId));
				
				if (specNamesId < 0) {
					throw new AndrolibException(String.format(
							"writeEntry new specNamesId < 0 %d", specNamesId));
				}
//			} else {
//				throw new AndrolibException(String.format(
//						"writeEntry can not found specNamesId %d", specNamesId));
//			}
		}
		mOut.writeInt(specNamesId);
		
		if ((flags & ENTRY_FLAG_COMPLEX) == 0) {
			writeValue();
		} else {
			writeComplexEntry();
		}
	}

	private void readComplexEntry(boolean flags, int specNamesId) throws IOException,
			AndrolibException {
		int parent = mIn.readInt();
		int count = mIn.readInt();
//		System.out.printf("readComplexEntry count  %d\n",count);
		for (int i = 0; i < count; i++) {
			mIn.readInt();
			readValue(flags, specNamesId);
		}


	}
	
	private void writeComplexEntry() throws IOException,
			AndrolibException {
		mOut.writeInt(mIn.readInt());
		int count = mIn.readInt();
		mOut.writeInt(count);
//		System.out.printf("writeComplexEntry count  %d\n",count);

		for (int i = 0; i < count; i++) {
			mOut.writeInt(mIn.readInt());
			writeValue();
		}
	}

	private void readValue(boolean flags, int specNamesId) throws IOException, AndrolibException {
		/* size */mIn.skipCheckShort((short) 8);
		/* zero */mIn.skipCheckByte((byte) 0);
		byte type = mIn.readByte();
		int data = mIn.readInt();
		
		//这里面有几个限制，一对于string ,id, array我们是知道肯定不用改的，第二看要那个type是否对应有文件路径
		if (mPkg.isCanProguard() && flags && type == TypedValue.TYPE_STRING && mShouldProguardForType && mShouldProguardTypeSet.contains(mType.getName())) {
			if (mTableStringsProguard.get(data) == null) {
				String raw = mTableStrings.get(data).toString();
				
				String proguard = mPkg.getSpecRepplace(mResId);
//				if (mType.getName().equals("drawable")) {
//					System.out.printf("after compatibaleraw %s, flag %b, type %d\n",mTableStrings.get(data), flags, type);
//				}
				
				//这个要写死这个，因为resources.arsc里面就是用这个
				int secondSlash = raw.lastIndexOf("/");

				if (secondSlash == -1) {
					throw new AndrolibException(String.format(
							"can not find \\ or raw string in res path=%s",
							raw));
				}
				
				String newFilePath = mOldFileName.get(raw.substring(0, secondSlash));
				
				if (newFilePath == null) {
//					throw new AndrolibException(String.format(
//							"can not found new res path, raw=%s",
//							raw));
					System.err.printf("can not found new res path, raw=%s\n",
							raw);
					return;
				}
				
				
				//同理这里不能用File.separator，因为resources.arsc里面就是用这个
				String result = newFilePath +"/" + proguard;
				
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
				
				
//				System.out.printf("after raw %s, result %s\n",raw,result);

				File resRawFile = new File (mApkDecoder.getOutTempDir().getAbsolutePath() + File.separator + compatibaleraw);
				File resDestFile = new File (mApkDecoder.getOutDir().getAbsolutePath() + File.separator + compatibaleresult);
				
				//这里用的是linux的分隔符
				HashMap<String, Integer> compressData = mApkDecoder.getCompressData(); 
				if (compressData.containsKey(raw)) {
					compressData.put(result, compressData.get(raw));
//					return;
				} else {
					System.err.printf("can not find the compress dataresFile=%s\n",
							raw);
				}

								
				if (!resRawFile.exists()) {
					System.err.printf("can not find res file, you delete it? path: resFile=%s\n",
							resRawFile.getAbsolutePath());
						return;
//					throw new AndrolibException(String.format(
//							"res file can not found: resFile=%s",
//							resRawFile.getAbsolutePath()));
				} else {
					if (resDestFile.exists()) {
						throw new AndrolibException(String.format(
								"res dest file is already  found: destFile=%s",
								resDestFile.getAbsolutePath()));
					}
					FileOperation.copyFileUsingStream(resRawFile, resDestFile);
					mTableStringsProguard.put(data, result);
				}

			}
			
		}
//		if (type == TypedValue.TYPE_STRING && !mTypeNames.getString(mCurTypeID - 1).equals("string")) {
//			System.out.printf("readValue type  %d, data %s\n",type, mTableStrings.getHTML(data));
//		}

		/*return type == TypedValue.TYPE_STRING ? mPkg.getValueFactory().factory(
				mTableStrings.getHTML(data)) : mPkg.getValueFactory().factory(
				type, data, null);*/
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
//		System.out.printf("writeValue type  %d, data %d\n",type, data);
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
		
		char[] language = new char[] { (char) mIn.readByte(), (char) mIn.readByte() };
		char[] country = new char[] { (char) mIn.readByte(), (char) mIn.readByte() };
		
		byte orientation = mIn.readByte();
		byte touchscreen = mIn.readByte();
		
		int density = mIn.readUnsignedShort();
		
		byte keyboard = mIn.readByte();
		byte navigation = mIn.readByte();
		byte inputFlags = mIn.readByte();
		/* inputPad0 */mIn.skipBytes(1);
		
		short screenWidth = mIn.readShort();
		short screenHeight = mIn.readShort();
		
		short sdkVersion = mIn.readShort();
		/* minorVersion, now must always be 0 */mIn.skipBytes(2);
		
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
	
	private void writeConfigFlags() throws IOException, AndrolibException {
		//总的有多大
		int size = mIn.readInt();
		if (size < 28) {
			throw new AndrolibException("Config size < 28");
		}
		mOut.writeInt(size);
		
		mOut.writeBytes(mIn, size -4);
		
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
	
	private Header writeNextChunk(int diffSize) throws IOException, AndrolibException {
		mHeader = Header.readAndWriteHeader(mIn, mOut, diffSize);
		return mHeader;
	}
	
	private Header writeNextChunkCheck(int expectedType, int diffSize) throws IOException,AndrolibException {
		mHeader = Header.readAndWriteHeader(mIn, mOut, diffSize);
		if (mHeader.type != expectedType) {
			throw new AndrolibException(String.format(
					"Invalid chunk type: expected=%d, got=%d",
					expectedType, mHeader.type ));
		}
		return mHeader;
	}

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

	private final static short ENTRY_FLAG_COMPLEX = 0x0001;
	
	
	public static class Header {
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
			} catch (EOFException ex) {
				return new Header(TYPE_NONE, 0);
			}
			in.skipBytes(2);

			return new Header(type, in.readInt());
		}
		
		public static Header readAndWriteHeader(ExtDataInput in,ExtDataOutput out, int diffSize) throws IOException, AndrolibException {
			short type = -1;
			int size = -1;
			try {
				type = in.readShort();
				out.writeShort(type);
				out.writeBytes(in, 2);
				size = in.readInt();
				size -= diffSize;
				if (size <= 0) {
					throw new AndrolibException(String.format(
							"readAndWriteHeader size < 0: size=%d",
							size));
				}
				out.writeInt(size);

			} catch (EOFException ex) {
				return new Header(TYPE_NONE, 0);
			}
			return new Header(type, size);
		}

		public final static short TYPE_NONE = -1, TYPE_TABLE = 0x0002,
				TYPE_PACKAGE = 0x0200, TYPE_TYPE = 0x0202,
				TYPE_CONFIG = 0x0201;
	}

	public static class FlagsOffset {
		public final int offset;
		public final int count;

		public FlagsOffset(int offset, int count) {
			this.offset = offset;
			this.count = count;
		}
	}

	private static final Logger LOGGER = Logger.getLogger(ARSCDecoder.class
			.getName());
	private static final int KNOWN_CONFIG_BYTES = 38;


	
	
	
	private int mCurTypeID = -1;
	private int mCurEntryID = -1;
	private int mCurPackageID = -1;

	private ProguardStringBuilder mProguardBuilder;
	private static Map<Integer, String>  mTableStringsProguard = new LinkedHashMap<Integer, String>();
	private boolean mShouldProguardForType = false;
	private Writer mMappingWriter;

//	private Map<Integer, Boolean> mTableStringDone = new LinkedHashMap<Integer, Boolean>();
	
	private Map<String, String> mOldFileName = new LinkedHashMap<String, String>();
	private Map<String, Integer> mCurSpecNameToPos = new LinkedHashMap<String, Integer>();
	private HashSet<String> mShouldProguardTypeSet = new HashSet<String>();

	private ApkDecoder mApkDecoder;
//	private File mOutTempARSCFile;
//	private File mOutARSCFile;
//	private File mOutDirFile;
//	private File mOutResFile;
//	private File mRawResFile;
//	private File mTempFile;
	
	private class ProguardStringBuilder {
		private int mReplaceCount = 0;
		private List<String> mReplaceStringBuffer = new ArrayList<String>();
		private boolean[] mIsReplaced;
		private boolean[] mIsWhiteList;
		private String[] mAToZ = {"a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z"};
		private String[] mAToAll = {"0","1","2","3","4","5","6","7","8","9","_","a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z"};
//		private String[] mAToZ = {"z","y","x","w","v","u","t","s","r","q","p","o","n","m","l","k","j","i","h","g","f","e","d","c","b","a"};
//		private String[] mAToAll = {"_","z","y","x","w","v","u","t","s","r","q","p","o","n","m","l","k","j","i","h","g","f","e","d","c","b","a","9","8","7","6","5","4","3","2","1","0"};
		public ProguardStringBuilder() {
			// TODO Auto-generated constructor stub
			mFileNameBlackList = new HashSet<String>();
			mFileNameBlackList.add("con");
			mFileNameBlackList.add("prn");
			mFileNameBlackList.add("aux");
			mFileNameBlackList.add("nul");

		}
	
		/**
		 * 在window上面有些关键字是不能作为文件名的
		 *  CON, PRN, AUX, CLOCK$, NUL
		 *	COM1, COM2, COM3, COM4, COM5, COM6, COM7, COM8, COM9
		 *	LPT1, LPT2, LPT3, LPT4, LPT5, LPT6, LPT7, LPT8, and LPT9. 
		 */
		private HashSet<String> mFileNameBlackList;
		
		
		public void reset() {
			mReplaceStringBuffer.clear();
			for (int i = 0; i< mAToZ.length; i++) {
				mReplaceStringBuffer.add(mAToZ[i]);
			}
			
			for (int i = 0; i< mAToZ.length; i++) {
				String first = mAToZ[i];
				for (int j = 0; j < mAToAll.length; j++) {
					String second = mAToAll[j];
					mReplaceStringBuffer.add(first + second);
				}
			}
			
			
			for (int i = 0; i< mAToZ.length; i++) {
				String first = mAToZ[i];
				for (int j = 0; j < mAToAll.length; j++) {
					String second = mAToAll[j];
					for (int k = 0; k < mAToAll.length; k++) {
						String third = mAToAll[k];
						String result = first + second + third;
						if (!mFileNameBlackList.contains(result))
							mReplaceStringBuffer.add(first + second + third);
					}
				}
			}
			mReplaceCount = 3;
			
			final int size = mReplaceStringBuffer.size();
			mIsReplaced = new boolean[size];
			mIsWhiteList = new boolean[size];
			for (int i = 0; i < size; i++) {
				mIsReplaced[i] = false;
				mIsWhiteList[i] = false;
			}
		}
		
		//对于某种类型用过的mapping，全部不能再用了
		public void removeStrings(Collection<String> collection) {
//    		System.out.printf("size %d\n", mReplaceStringBuffer.size());

    		mReplaceStringBuffer.removeAll(collection);
//    		System.out.printf("after size %d\n", mReplaceStringBuffer.size());

		}
		
		public boolean isReplaced(int id) {
			return mIsReplaced[id];
		}
		
		public boolean isInWhiteList(int id) {
			return mIsWhiteList[id];
		}
		
		public void setInWhiteList(int id, boolean set) {
			 mIsWhiteList[id] = set;
		}
		
		public void setInReplaceList(int id, boolean set) {
			mIsReplaced[id] = set;
		}
		
		
		//开始设计是根据id来get,但是为了实现保持mapping的方式，取消了这个
		public String getReplaceString() throws AndrolibException {
			if (mReplaceStringBuffer.isEmpty()) {
				throw new AndrolibException(String.format(
						"now can only proguard less than 35594 in a single type\n"
						));
			}
			
			
			return mReplaceStringBuffer.remove(0);
		}
		
		public int lenght() {
			return mReplaceStringBuffer.size();
		}
		
	}
	

	
	/**
	 * 为了加速，不需要处理string,id,array，这几个是肯定不是的
	 * @param name
	 * @return
	 */
	private boolean isToProguardFile(String name) {
		return (!name.equals("string") && !name.equals("id") && !name.equals("array"));
	}
}