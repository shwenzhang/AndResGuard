
package com.tencent.mm.androlib;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


import com.tencent.mm.resourceproguard.Main;
import com.tencent.mm.util.FileOperation;
import com.tencent.mm.util.TypedValue;

/**
 * @author shwenzhang
 *
 */
public class ResourceApkBuilder {
	public ResourceApkBuilder(Main m) {
		mClient = m;
	}

	private Main mClient;
	private File mOutDir;
	private File m7zipOutPutDir;

	private File mUnSignedApk;
	private File mSignedApk;
	private File mSignedWith7ZipApk;

	private File mAlignedApk;
	private File mAlignedWith7ZipApk;
	
	private String mApkName;
	public void setOutDir(File outDir, String apkname) throws AndrolibException {
		mOutDir = outDir;
		mApkName = apkname;
	}
	
	public void buildApk(HashMap<String, Integer> compressData) throws IOException, InterruptedException {
		insureFileName();
		generalUnsignApk(compressData);
		signApk();
		use7zApk(compressData);
		alignApk();
		
	}
	
	private void insureFileName() {
		mUnSignedApk = new File(mOutDir.getAbsolutePath() + File.separator + mApkName + "_unsigned.apk");
		//需要自己安装7zip
		mSignedWith7ZipApk = new File(mOutDir.getAbsolutePath() + File.separator + mApkName + "_signed_7zip.apk");
		mSignedApk = new File(mOutDir.getAbsolutePath() + File.separator + mApkName + "_signed.apk");
		mAlignedApk = new File(mOutDir.getAbsolutePath() + File.separator + mApkName + "_signed_aligned.apk");
		mAlignedWith7ZipApk = new File(mOutDir.getAbsolutePath() + File.separator + mApkName + "_signed_7zip_aligned.apk");
		
		m7zipOutPutDir = new File(mOutDir.getAbsolutePath() + File.separator + TypedValue.OUT_7ZIP_FILE_PATH);

	}
	
	private void use7zApk(HashMap<String, Integer> compressData) throws IOException, InterruptedException {
		if (!mClient.isUse7zip()) {
			return;
		}
		
		if (!mClient.isUseSignAPk()) {
			throw new IOException(
					"if you want to use 7z, you must set the sign issue to active in the config file first");
		}
		
		if (!mSignedApk.exists()) {
			throw new IOException(String.format(
					"can not found the signed apk file to 7z, if you want to use 7z, you must fill the sign data in the config file" +
					", path=%s",
					mSignedApk.getAbsolutePath()));
		}
		

		System.out.printf("use 7zip to repackage: %s, will cost much more time\n", mSignedWith7ZipApk.getName());
		FileOperation.unZipAPk(mSignedApk.getAbsolutePath(), m7zipOutPutDir.getAbsolutePath());
		//首先一次性生成一个全部都是压缩的安装包
		generalRaw7zip();
		
		ArrayList<String> storedFiles = new ArrayList<String>();
		//对于不压缩的要update回去
		for(String name : compressData.keySet()) {
			File file = new File(m7zipOutPutDir.getAbsolutePath() + File.separator + name);
			if (!file.exists()) {
				continue;
//					System.err.printf("file does not exit in compress data, path=%s\n", file.getAbsolutePath());
			}
			int method = compressData.get(name);
//				System.out.printf("name %s\n", name);
			if (method == TypedValue.ZIP_STORED) {
				storedFiles.add(name);
			}
		}
			
		addStoredFileIn7Zip(storedFiles);
		
		if (!mSignedWith7ZipApk.exists()) {
			throw new IOException(String.format(
					"7z repackage signed apk fail,you must install 7z command line version first, linux: p7zip, window: 7za, path=%s",
					mSignedWith7ZipApk.getAbsolutePath()));
		}
		
		System.out.printf("use 7zip to repackage %s done, file reduce: %fkb, time cost from begin: %fs\n", mSignedWith7ZipApk.getName(),
				mClient.diffApkSizeFromRaw(FileOperation.getFileSizes(mSignedWith7ZipApk)), mClient.diffTimeFromBegin());
		
	}
	
	private void signApk() throws IOException, InterruptedException {
		//尝试去对apk签名
		if (mClient.isUseSignAPk()) {
			System.out.printf("signing apk: %s\n",mSignedApk.getName());

//			int version = Integer.parseInt(System.getProperty("java.version").split("\\.")[1]);
//			System.out.printf("version %d\n", version);
			if (mSignedApk.exists()) {
				mSignedApk.delete();
			}
			
			String cmd = "jarsigner -sigalg MD5withRSA -digestalg SHA1 -keystore "+mClient.getSignatureFile()+ " -storepass "  + mClient.getStorePass()+ " -keypass " + mClient.getKeyPass()
					+ " -signedjar " + mSignedApk.getAbsolutePath() + " " + mUnSignedApk.getAbsolutePath() + " " + mClient.getStoreAlias();
//	    	System.out.println("cmd "+cmd );
//		    	System.out.println("outputPath "+outputPath);
//		    	System.out.println("outputTempPath "+outputTempPath);

	        Process pro;
	     
			
			pro = Runtime.getRuntime().exec(cmd);
			
			//destroy the stream
			pro.waitFor();
			pro.destroy();
			
			if (!mSignedApk.exists()) {
				throw new IOException(String.format(
						"can not found the signed apk file, is the input sign data correct? path=%s",
						mSignedApk.getAbsolutePath()));
			}
			
			System.out.printf("sign apk %s done, file reduce: %fkb, time cost from begin: %fs\n", mSignedApk.getName(),
					mClient.diffApkSizeFromRaw(FileOperation.getFileSizes(mSignedApk)), mClient.diffTimeFromBegin());
			
			
		}
		
	}
	
	private void alignApk() throws IOException, InterruptedException {
		//如果不签名就肯定不需要对齐了
		if (!mClient.isUseSignAPk()) {
			return;
		}
		if (mSignedWith7ZipApk.exists()) {
			if (mSignedApk.exists()) {
				alignApk(mSignedApk, mAlignedApk);
			}
			alignApk(mSignedWith7ZipApk, mAlignedWith7ZipApk);
		} else if (mSignedApk.exists()) {
			alignApk(mSignedApk, mAlignedApk);
		} else {
			throw new IOException(
					"can not found any signed apk file"
				);
		}
	}
	
	private void alignApk(File before, File after) throws IOException, InterruptedException {
		System.out.printf("zipaligning apk: %s\n", before.getName());
		if (!before.exists()) {
			throw new IOException(String.format(
					"can not found the raw apk file to zipalign, path=%s",
					before.getAbsolutePath()));
		}
		String cmd;
		if (mClient.getZipalignPath() == null) {
			cmd = "zipalign";
		} else {
			cmd = mClient.getZipalignPath();
		}
		cmd += " 4 " + before.getAbsolutePath() + " " + after.getAbsolutePath();;
//	    	System.out.println("cmd "+cmd );
//	    	System.out.println("outputPath "+outputPath);
//	    	System.out.println("outputTempPath "+outputTempPaWth);

        Process pro;
     
		
		pro = Runtime.getRuntime().exec(cmd);
		
		//destroy the stream
		pro.waitFor();
		pro.destroy();
			
		
		
		if (!after.exists()) {
			throw new IOException(String.format(
					"can not found the aligned apk file, the ZipAlign path is correct? path=%s",
					mAlignedApk.getAbsolutePath()));
		}
		
		System.out.printf("zipaligning apk %s done, file reduce: %fkb, time cost from begin: %fs\n", after.getName(),
				mClient.diffApkSizeFromRaw(FileOperation.getFileSizes(after)), mClient.diffTimeFromBegin());
		
	}
	
	private void generalUnsignApk(HashMap<String, Integer> compressData) throws IOException, InterruptedException {
		System.out.printf("general unsigned apk: %s\n", mUnSignedApk.getName());

		File tempOutDir = new File(mOutDir.getAbsolutePath() + File.separator + TypedValue.UNZIP_FILE_PATH);
		
		if (!tempOutDir.exists()) {
			System.err.printf("Missing apk unzip files, path=%s\n", tempOutDir.getAbsolutePath());
            System.exit(-1);
		}
		

		File[] unzipFiles = tempOutDir.listFiles();
		List<File> collectFiles = new ArrayList<File>();
		for (File f : unzipFiles) {
			String name = f.getName();
			if (name.equals("res") || name.equals(mClient.getMetaName()) || name.equals("resources.arsc")) {
				continue;
			}
			collectFiles.add(f);
		}
		
		//添加修改后的res文件
		File destResDir = new File(mOutDir.getAbsolutePath() + File.separator + TypedValue.RES_FILE_PATH);
//		System.err.printf("destpath count=%d\n", FileOperation.getlist(rawResDir));
		
		//!!!文件数量应该是一样的，如果不一样肯定有问题
		File rawResDir = new File(tempOutDir.getAbsolutePath() + File.separator + "res");
		
		if (FileOperation.getlist(destResDir) !=  FileOperation.getlist(rawResDir)) {
			throw new IOException(String.format(
					"the file count of %s, and the file count of %s is not equal, there must be some problem, please contact shwenzhang for detail\n",
					rawResDir.getAbsolutePath(), destResDir.getAbsolutePath()));
		}

//		System.err.printf("rawpath count=%d\n", FileOperation.getlist(new File(tempOutDir.getAbsolutePath() + File.separator + "res")));
		if (!destResDir.exists()) {
			System.err.printf("Missing res files, path=%s\n", destResDir.getAbsolutePath());
            System.exit(-1);
		}
		
		//这个需要检查混淆前混淆后，两个res的文件数量是否相等
		
		collectFiles.add(destResDir);
		
		File rawARSCFile = new File(mOutDir.getAbsolutePath() + File.separator + "resources.arsc");
		if (!rawARSCFile.exists()) {
			System.err.printf("Missing resources.arsc files, path=%s\n", rawARSCFile.getAbsolutePath());
            System.exit(-1);
		}
		collectFiles.add(rawARSCFile);
		
		FileOperation.zipFiles(collectFiles, mUnSignedApk, compressData);
		
		if (!mUnSignedApk.exists()) {
			throw new IOException(String.format(
					"can not found the unsign apk file path=%s",
					mUnSignedApk.getAbsolutePath()));
		}
		
		System.out.printf("general unsigned apk %s done, file reduce: %fkb, time cost from begin: %fs\n", mUnSignedApk.getName(),
				mClient.diffApkSizeFromRaw(FileOperation.getFileSizes(mUnSignedApk)), mClient.diffTimeFromBegin());

	}
	
	private void addStoredFileIn7Zip(ArrayList<String> storedFiles) throws IOException, InterruptedException {
		System.out.printf("rewrite the stored file into the 7zip, file count:%d\n", storedFiles.size());

		String storedParentName = mOutDir.getAbsolutePath() + File.separator + "storefiles" + File.separator;
		String outputName = m7zipOutPutDir.getAbsolutePath() + File.separator;
		for(String name : storedFiles) {
			FileOperation.copyFileUsingStream(new File(outputName + name), new File(storedParentName + name));

		}
		Process pro = null;
		
		storedParentName = storedParentName + File.separator +"*";
		
		//极限压缩
		String cmd;
		if (mClient.get7zipPath() == null) {
			cmd = TypedValue.COMMAND_7ZIP;
		} else {
			cmd = mClient.get7zipPath();
		}
		cmd += " a -tzip "+ mSignedWith7ZipApk.getAbsolutePath()+ " " + storedParentName + " -mx0";
//		if ()
//		System.out.println("cmd "+cmd );
		pro = Runtime.getRuntime().exec(cmd);
	
        InputStreamReader ir = null;
        LineNumberReader input = null;
			
		ir = new InputStreamReader(pro.getInputStream());

		input = new LineNumberReader(ir);
		
		
		String line;
		//如果不读会有问题，被阻塞
		while ((line = input.readLine()) != null) {
//			System.out.println(line);
		}
		
		//destroy the stream
		if (pro != null) {
			pro.waitFor();
			pro.destroy();
		}
	}
	
	private void generalRaw7zip() throws IOException, InterruptedException {
		System.out.printf("general the raw 7zip file\n");
		Process pro = null;
		String outPath = m7zipOutPutDir.getAbsoluteFile().getAbsolutePath();
	
		String path = outPath + File.separator +"*";
		
		//极限压缩
		String cmd;
		if (mClient.get7zipPath() == null) {
			cmd = TypedValue.COMMAND_7ZIP;
		} else {
			cmd = mClient.get7zipPath();
		}
		cmd += " a -tzip "+ mSignedWith7ZipApk.getAbsolutePath()+ " " + path + " -mx9";
//		if ()
//		System.out.println("cmd "+cmd );
		pro = Runtime.getRuntime().exec(cmd);
	
        InputStreamReader ir = null;
        LineNumberReader input = null;
			
		ir = new InputStreamReader(pro.getInputStream());

		input = new LineNumberReader(ir);
		
		
		String line;
		//如果不读会有问题，被阻塞
		while ((line = input.readLine()) != null) {
//			System.out.println(line);
		}
		
		//destroy the stream
		if (pro != null) {
			pro.waitFor();
			pro.destroy();
		}
	}
}
