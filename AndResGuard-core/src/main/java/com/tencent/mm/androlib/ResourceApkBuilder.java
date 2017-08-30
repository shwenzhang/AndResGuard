package com.tencent.mm.androlib;

import apksigner.ApkSignerTool;
import com.tencent.mm.resourceproguard.Configuration;
import com.tencent.mm.util.FileOperation;
import com.tencent.mm.util.TypedValue;
import com.tencent.mm.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author shwenzhang
 * modified:
 * @author jonychina162
 *         为了使用v2签名，引入了google v2sign 模块
 *         由于使用v2签名，会对整个包除了签名块验证完整性，即除了签名块的内容在签名之后包其他内容不允许再改动，因此修改了原有的签名逻辑，
 *         现有逻辑：1 zipalign 2.sign 。具体请参考buildApkV2sign
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

    public void setOutDir(File outDir, String apkName) throws AndrolibException {
        mOutDir = outDir;
        mApkName = apkName;
    }

    public void buildApkV1sign(HashMap<String, Integer> compressData) throws IOException, InterruptedException {
        insureFileNameV1();
        generalUnsignApk(compressData);
        signApkV1(mUnSignedApk , mSignedApk);
        use7zApk(compressData);
        alignApks();
    }

    public void buildApkV2sign(HashMap<String, Integer> compressData) throws Exception {
        insureFileNameV2();
        generalUnsignApk(compressData);
        /*
         * Caution: If you sign your app using APK Signature Scheme v2 and make further changes to the app,
         * the app's signature is invalidated.
         * For this reason, use tools such as zipalign before signing your app using APK Signature Scheme v2, not after.
         */
        alignApk(mUnSignedApk, mAlignedApk);
        signApkV2(mAlignedApk, mSignedApk);
    }

    private void insureFileNameV1() {
        mUnSignedApk = new File(mOutDir.getAbsolutePath(), mApkName + "_unsigned.apk");
        mSignedWith7ZipApk = new File(mOutDir.getAbsolutePath(), mApkName + "_signed_7zip.apk");
        mSignedApk = new File(mOutDir.getAbsolutePath(), mApkName + "_signed.apk");
        mAlignedApk = new File(mOutDir.getAbsolutePath(), mApkName + "_signed_aligned.apk");
        mAlignedWith7ZipApk = new File(mOutDir.getAbsolutePath(), mApkName + "_signed_7zip_aligned.apk");
        m7zipOutPutDir = new File(mOutDir.getAbsolutePath(), TypedValue.OUT_7ZIP_FILE_PATH);
    }

    private void insureFileNameV2() {
        mUnSignedApk = new File(mOutDir.getAbsolutePath(), mApkName + "_unsigned.apk");
        mAlignedApk = new File(mOutDir.getAbsolutePath(), mApkName + "_aligned_unsigned.apk");
        mSignedApk = new File(mOutDir.getAbsolutePath(), mApkName + "_aligned_signed.apk");
    }

    private void use7zApk(HashMap<String, Integer> compressData) throws IOException, InterruptedException {
        if (!config.mUse7zip) {
            return;
        }
        if (!config.mUseSignAPK) {
            throw new IOException("if you want to use 7z, you must enable useSign in the config file first");
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

        ArrayList<String> storedFiles = new ArrayList<>();
        //对于不压缩的要update回去
        for (String name : compressData.keySet()) {
            File file = new File(m7zipOutPutDir.getAbsolutePath(), name);
            if (!file.exists()) {
                continue;
            }
            int method = compressData.get(name);
            if (method == TypedValue.ZIP_STORED) {
                storedFiles.add(name);
            }
        }

        addStoredFileIn7Zip(storedFiles);
        if (!mSignedWith7ZipApk.exists()) {
            throw new IOException(String.format(
                "[use7zApk]7z repackage signed apk fail,you must install 7z command line version first, linux: p7zip, window: 7za, path=%s",
                mSignedWith7ZipApk.getAbsolutePath()));
        }
    }

    private String getSignatureAlgorithm() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        FileInputStream fileIn = new FileInputStream(config.mSignatureFile);
        keyStore.load(fileIn, config.mStorePass.toCharArray());
        Key key = keyStore.getKey(config.mStoreAlias, config.mKeyPass.toCharArray());
        if (key == null) {
            throw new RuntimeException(
                "Can't get private key, please check if storepass storealias and keypass are correct"
            );
        }
        String keyAlgorithm = key.getAlgorithm();
        String signatureAlgorithm;
        if (keyAlgorithm.equalsIgnoreCase("DSA")) {
            signatureAlgorithm = "SHA1withDSA";
        } else if (keyAlgorithm.equalsIgnoreCase("RSA")) {
            signatureAlgorithm = "SHA1withRSA";
        } else if (keyAlgorithm.equalsIgnoreCase("EC")) {
            signatureAlgorithm = "SHA1withECDSA";
        } else {
            throw new RuntimeException("private key is not a DSA or RSA key");
        }
        System.out.printf("signature Algorithm is: %s\n", signatureAlgorithm);
        return signatureAlgorithm;
    }

    private void signApkV1(File unSignedApk, File signedApk) throws IOException, InterruptedException {
        if (config.mUseSignAPK) {
            System.out.printf("signing apk: %s\n", signedApk.getName());
            if (signedApk.exists()) {
                signedApk.delete();
            }
            signWithV1sign(unSignedApk, signedApk);
            if (!signedApk.exists()) {
                throw new IOException("Can't Generate signed APK. Plz check your v1sign info is correct.");
            }
        }
    }

    private void signApkV2(File unSignedApk, File signedApk) throws Exception {
        if (config.mUseSignAPK) {
            System.out.printf("signing apk: %s\n", signedApk.getName());
            signWithV2sign(unSignedApk, signedApk);
            if (!signedApk.exists()) {
                throw new IOException("Can't Generate signed APK v2. Plz check your v2sign info is correct.");
            }
        }
    }

    private void signWithV2sign(File unSignedApk, File signedApk) throws Exception {
        String[] params = new String[]{
            "sign",
            "--ks", config.mSignatureFile.getAbsolutePath(),
            "--ks-pass", "pass:" + config.mStorePass,
            "--ks-key-alias", config.mStoreAlias,
            "--key-pass","pass:" + config.mKeyPass,
            "--out", signedApk.getAbsolutePath(),
            unSignedApk.getAbsolutePath()
        };
        //dumpParams(params);
        ApkSignerTool.main(params);
    }

    private void signWithV1sign(File unSignedApk, File signedApk) throws IOException, InterruptedException {
        String signatureAlgorithm = "MD5withRSA";
        try {
            signatureAlgorithm = getSignatureAlgorithm();
        } catch (Exception e) {
            e.printStackTrace();
        }
        String[] argv = {
            "jarsigner",
            "-sigalg", signatureAlgorithm,
            "-digestalg", "SHA1",
            "-keystore", config.mSignatureFile.getAbsolutePath(),
            "-storepass", config.mStorePass,
            "-keypass", config.mKeyPass,
            "-signedjar", signedApk.getAbsolutePath(),
            unSignedApk.getAbsolutePath(),
            config.mStoreAlias
        };
        //dumpParams(argv);
        Process pro = null;
        try {
            pro = Runtime.getRuntime().exec(argv);
            //destroy the stream
            pro.waitFor();
        } finally {
            if (pro != null) {
                pro.destroy();
            }
        }
    }

    private void dumpParams(String[] params) {
        StringBuilder sb = new StringBuilder();
        for (String param : params) {
            sb.append(param).append(" ");
        }
        System.out.println(sb.toString());
    }

    private void alignApks() throws IOException, InterruptedException {
        //如果不签名就肯定不需要对齐了
        if (!config.mUseSignAPK) {
            return;
        }
        if (!mSignedApk.exists() && !mSignedWith7ZipApk.exists()) {
            throw new IOException("Can not found any signed apk file");
        }
        if (mSignedApk.exists()) {
            alignApk(mSignedApk, mAlignedApk);
        }
        if (mSignedWith7ZipApk.exists()) {
            alignApk(mSignedWith7ZipApk, mAlignedWith7ZipApk);
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
        System.out.printf("General unsigned apk: %s\n", mUnSignedApk.getName());
        File tempOutDir = new File(mOutDir.getAbsolutePath(), TypedValue.UNZIP_FILE_PATH);
        if (!tempOutDir.exists()) {
            System.err.printf("Missing apk unzip files, path=%s\n", tempOutDir.getAbsolutePath());
            System.exit(-1);
        }

        File[] unzipFiles = tempOutDir.listFiles();
        assert unzipFiles != null;
        List<File> collectFiles = new ArrayList<>();
        for (File f : unzipFiles) {
            String name = f.getName();
            if (name.equals("res") || name.equals("resources.arsc")) {
                continue;
            } else if (name.equals(config.mMetaName)) {
                addNonSignatureFiles(collectFiles, f);
                continue;
            }
            collectFiles.add(f);
        }

        File destResDir = new File(mOutDir.getAbsolutePath(), "res");
        //添加修改后的res文件
        if (!config.mKeepRoot && FileOperation.getlist(destResDir) == 0) {
            destResDir = new File(mOutDir.getAbsolutePath(), TypedValue.RES_FILE_PATH);
        }

        /*
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
        FileOperation.zipFiles(collectFiles, tempOutDir, mUnSignedApk, compressData);

        if (!mUnSignedApk.exists()) {
            throw new IOException(String.format(
                "can not found the unsign apk file path=%s",
                mUnSignedApk.getAbsolutePath()));
        }
    }

    private void addNonSignatureFiles(List<File> collectFiles, File metaFolder) {
        File[] metaFiles = metaFolder.listFiles();
        if (metaFiles != null) {
            for (File metaFile : metaFiles) {
                String metaFileName = metaFile.getName();
                // Ignore signature files
                if (!metaFileName.endsWith(".MF") && !metaFileName.endsWith(".RSA")
                  && !metaFileName.endsWith(".SF")) {
                    System.out.println(String.format("add meta file %s", metaFile.getAbsolutePath()));
                    collectFiles.add(metaFile);
                }
            }
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
        ProcessBuilder pb = new ProcessBuilder(cmd, "a", "-tzip", mSignedWith7ZipApk.getAbsolutePath(), storedParentName, "-mx0");
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
        ProcessBuilder pb = new ProcessBuilder(cmd, "a", "-tzip", mSignedWith7ZipApk.getAbsolutePath(), path, "-mx9");
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
