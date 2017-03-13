package com.tencent.mm.resourceproguard;

import com.tencent.mm.androlib.AndrolibException;
import com.tencent.mm.androlib.ApkDecoder;
import com.tencent.mm.androlib.ResourceApkBuilder;
import com.tencent.mm.androlib.res.decoder.ARSCDecoder;
import com.tencent.mm.directory.DirectoryException;
import com.tencent.mm.util.FileOperation;

import java.io.File;
import java.io.IOException;


/**
 * @author shwenzhang
 * @author simsun
 */
public class Main {


    public static final int ERRNO_ERRORS = 1;
    public static final int ERRNO_USAGE  = 2;
    protected static long          mRawApkSize;
    protected static String        mRunningLocation;
    protected static long          mBeginTime;

    /** 是否通过命令行方式设置 **/
    public boolean mSetSignThroughCmd    = false;
    public boolean mSetMappingThroughCmd = false;
    public String  m7zipPath             = null;
    public String  mZipalignPath         = null;
    protected        Configuration config;
    protected        File          mOutDir;

    public static void gradleRun(InputParam inputParam) {
        Main m = new Main();
        m.run(inputParam);
    }

    private void run(InputParam inputParam) {
        loadConfigFromGradle(inputParam);
        System.out.println("AndResGuard starting...");
        resourceProguard(new File(inputParam.outFolder), inputParam.apkPath, inputParam.signatureType);
        System.out.printf("AndResGuard done, you can go to file to find the output %s\n", mOutDir.getAbsolutePath());
        clean();
    }

    protected void clean() {
        config = null;
        ARSCDecoder.mTableStringsProguard.clear();
    }

    private void loadConfigFromGradle(InputParam inputParam) {
        try {
            config = new Configuration(inputParam);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void resourceProguard(File outputFile, String apkFilePath, InputParam.SignatureType signatureType) {
        ApkDecoder decoder = new ApkDecoder(config);
        File apkFile = new File(apkFilePath);
        if (!apkFile.exists()) {
            System.err.printf("the input apk %s does not exit", apkFile.getAbsolutePath());
            goToError();
        }
        mRawApkSize = FileOperation.getFileSizes(apkFile);
        try {
            /* 默认使用V1签名 */
            decodeResource(outputFile, decoder, apkFile);
            buildApk(decoder, apkFile, signatureType);
        } catch (Exception e) {
            e.printStackTrace();
            goToError();
        }
    }

    private void decodeResource(File outputFile, ApkDecoder decoder, File apkFile) throws AndrolibException, IOException, DirectoryException {
        decoder.setApkFile(apkFile);
        if (outputFile == null) {
            mOutDir = new File(mRunningLocation, apkFile.getName().substring(0, apkFile.getName().indexOf(".apk")));
        } else {
            mOutDir = outputFile;
        }
        decoder.setOutDir(mOutDir.getAbsoluteFile());
        decoder.decode();
    }

    private void buildApk(ApkDecoder decoder, File apkFile, InputParam.SignatureType signatureType) throws Exception {
        ResourceApkBuilder builder = new ResourceApkBuilder(config);
        String apkBasename = apkFile.getName();
        apkBasename = apkBasename.substring(0, apkBasename.indexOf(".apk"));
        builder.setOutDir(mOutDir, apkBasename);
        switch (signatureType) {
            case SchemaV1:
                builder.buildApkV1sign(decoder.getCompressData());
                break;
            case SchemaV2:
                builder.buildApkV2sign(decoder.getCompressData());
                break;
        }
    }

    protected void goToError() {
        System.exit(ERRNO_USAGE);
    }
}