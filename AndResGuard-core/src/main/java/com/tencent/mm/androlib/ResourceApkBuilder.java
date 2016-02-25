package com.tencent.mm.androlib;

import com.tencent.mm.resourceproguard.Configuration;
import com.tencent.mm.util.FileOperation;
import com.tencent.mm.util.TypedValue;
import com.tencent.mm.util.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author shwenzhang
 */
public class ResourceApkBuilder {

    private final Configuration config;
    private       File          mOutDir;
    private       File          m7zipOutPutDir;

    private File mUnSignedApk;
    private File mSignedApk;
    private File mSignedWith7ZipApk;

    private File mAlignedApk;
    private File mAlignedWith7ZipApk;

    private String mApkName;

    public ResourceApkBuilder(Configuration config) {
        this.config = config;
    }

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
        mUnSignedApk = new File(mOutDir.getAbsolutePath(), mApkName + "_unsigned.apk");
        //需要自己安装7zip
        mSignedWith7ZipApk = new File(mOutDir.getAbsolutePath(), mApkName + "_signed_7zip.apk");
        mSignedApk = new File(mOutDir.getAbsolutePath(), mApkName + "_signed.apk");
        mAlignedApk = new File(mOutDir.getAbsolutePath(), mApkName + "_signed_aligned.apk");
        mAlignedWith7ZipApk = new File(mOutDir.getAbsolutePath(), mApkName + "_signed_7zip_aligned.apk");
        m7zipOutPutDir = new File(mOutDir.getAbsolutePath(), TypedValue.OUT_7ZIP_FILE_PATH);
    }

    private void use7zApk(HashMap<String, Integer> compressData) throws IOException, InterruptedException {
        if (!config.mUse7zip) {
            return;
        }
        if (!config.mUseSignAPk) {
            throw new IOException("if you want to use 7z, you must set the sign issue to active in the config file first");
        }
        if (!mSignedApk.exists()) {
            throw new IOException(
                String.format("can not found the signed apk file to 7z, if you want to use 7z, " +
                    "you must fill the sign data in the config file path=%s", mSignedApk.getAbsolutePath())
            );
        }
        System.out.printf("use 7zip to repackage: %s, will cost much more time\n", mSignedWith7ZipApk.getName());
        FileOperation.unZipAPk(mSignedApk.getAbsolutePath(), m7zipOutPutDir.getAbsolutePath());
        //首先一次性生成一个全部都是压缩的安装包
        generalRaw7zip();

        ArrayList<String> storedFiles = new ArrayList<String>();
        //对于不压缩的要update回去
        for (String name : compressData.keySet()) {
            File file = new File(m7zipOutPutDir.getAbsolutePath() + File.separator + name);
            if (!file.exists()) {
                continue;
            }
            int method = compressData.get(name);
            if (method == TypedValue.ZIP_STORED) {
                storedFiles.add(name);
            }
        }

        addStoredFileIn7Zip(storedFiles);
        System.out.println(mSignedWith7ZipApk.exists());
        if (!mSignedWith7ZipApk.exists()) {
            throw new IOException(String.format(
                "[use7zApk]7z repackage signed apk fail,you must install 7z command line version first, linux: p7zip, window: 7za, path=%s",
                mSignedWith7ZipApk.getAbsolutePath()));
        }
    }

    private void signApk() throws IOException, InterruptedException {
        //尝试去对apk签名
        if (config.mUseSignAPk) {
            System.out.printf("signing apk: %s\n", mSignedApk.getName());
            if (mSignedApk.exists()) {
                mSignedApk.delete();
            }
            String cmd = "jarsigner -sigalg MD5withRSA -digestalg SHA1 -keystore " + config.mSignatureFile
                + " -storepass " + config.mStorePass
                + " -keypass " + config.mKeyPass
                + " -signedjar " + mSignedApk.getAbsolutePath()
                + " " + mUnSignedApk.getAbsolutePath()
                + " " + config.mStoreAlias;
            Process pro = Runtime.getRuntime().exec(cmd);
            //destroy the stream
            pro.waitFor();
            pro.destroy();

            if (!mSignedApk.exists()) {
                throw new IOException("Can't Generate signed APK. Plz check your sign info is correct.");
            }
        }
    }

    private void alignApk() throws IOException, InterruptedException {
        //如果不签名就肯定不需要对齐了
        if (!config.mUseSignAPk) {
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
            throw new IOException("can not found any signed apk file");
        }
    }

    private void alignApk(File before, File after) throws IOException, InterruptedException {
        System.out.printf("zipaligning apk: %s\n", before.getName());
        if (!before.exists()) {
            throw new IOException(String.format(
                "can not found the raw apk file to zipalign, path=%s",
                before.getAbsolutePath()));
        }
        String cmd = Utils.isPresent(config.mZipalignPath) ? config.mZipalignPath : TypedValue.COMMAND_ZIPALIGIN;
        ProcessBuilder pb = new ProcessBuilder(cmd, "4", before.getAbsolutePath(), after.getAbsolutePath());
        Process pro = pb.start();

        //destroy the stream
        pro.waitFor();
        pro.destroy();
        if (!after.exists()) {
            throw new IOException(
                String.format("can not found the aligned apk file, the ZipAlign path is correct? path=%s", mAlignedApk.getAbsolutePath())
            );
        }
    }

    private void generalUnsignApk(HashMap<String, Integer> compressData) throws IOException, InterruptedException {
        System.out.printf("general unsigned apk: %s\n", mUnSignedApk.getName());
        File tempOutDir = new File(mOutDir.getAbsolutePath(), TypedValue.UNZIP_FILE_PATH);
        if (!tempOutDir.exists()) {
            System.err.printf("Missing apk unzip files, path=%s\n", tempOutDir.getAbsolutePath());
            System.exit(-1);
        }

        File[] unzipFiles = tempOutDir.listFiles();
        List<File> collectFiles = new ArrayList<>();
        for (File f : unzipFiles) {
            String name = f.getName();
            if (name.equals("res") || name.equals(config.mMetaName) || name.equals("resources.arsc")) {
                continue;
            }
            collectFiles.add(f);
        }

        File destResDir = new File(mOutDir.getAbsolutePath(), "res");
        //添加修改后的res文件
        if (!config.mKeepRoot) {
            destResDir = new File(mOutDir.getAbsolutePath(), TypedValue.RES_FILE_PATH);
        }

        /**
         * NOTE:文件数量应该是一样的，如果不一样肯定有问题
         */
        File rawResDir = new File(tempOutDir.getAbsolutePath() + File.separator + "res");
        System.out.printf("DestResDir %d rawResDir %d\n", FileOperation.getlist(destResDir), FileOperation.getlist(rawResDir));
        if (FileOperation.getlist(destResDir) != FileOperation.getlist(rawResDir)) {
            throw new IOException(String.format(
                "the file count of %s, and the file count of %s is not equal, there must be some problem\n",
                rawResDir.getAbsolutePath(), destResDir.getAbsolutePath()));
        }
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
    }

    private void addStoredFileIn7Zip(ArrayList<String> storedFiles) throws IOException, InterruptedException {
        System.out.printf("[addStoredFileIn7Zip]rewrite the stored file into the 7zip, file count:%d\n", storedFiles.size());
        String storedParentName = mOutDir.getAbsolutePath() + File.separator + "storefiles" + File.separator;
        String outputName = m7zipOutPutDir.getAbsolutePath() + File.separator;
        for (String name : storedFiles) {
            FileOperation.copyFileUsingStream(new File(outputName + name), new File(storedParentName + name));
        }
        storedParentName = storedParentName + File.separator + "*";
        //极限压缩
        String cmd = Utils.isPresent(config.m7zipPath) ? config.m7zipPath : TypedValue.COMMAND_7ZIP;
        ProcessBuilder pb = new ProcessBuilder(cmd, "a -tzip", mSignedWith7ZipApk.getAbsolutePath(), storedParentName, "-mx0");
        Process pro = pb.start();

        InputStreamReader ir = new InputStreamReader(pro.getInputStream());
        LineNumberReader input = new LineNumberReader(ir);
        //如果不读会有问题，被阻塞
        while (input.readLine() != null) { }
        //destroy the stream
        pro.waitFor();
        pro.destroy();
    }

    private void generalRaw7zip() throws IOException, InterruptedException {
        String outPath = m7zipOutPutDir.getAbsoluteFile().getAbsolutePath();
        String path = outPath + File.separator + "*";
        //极限压缩
        String cmd = Utils.isPresent(config.m7zipPath) ? config.m7zipPath : TypedValue.COMMAND_7ZIP;
        ProcessBuilder pb = new ProcessBuilder(cmd, " a -tzip", mSignedWith7ZipApk.getAbsolutePath(), path, "-mx9");
        Process pro = pb.start();

        InputStreamReader ir = new InputStreamReader(pro.getInputStream());
        LineNumberReader input = new LineNumberReader(ir);
        //如果不读会有问题，被阻塞
        while (input.readLine() != null) { }
        //destroy the stream
        pro.waitFor();
        pro.destroy();
    }
}
