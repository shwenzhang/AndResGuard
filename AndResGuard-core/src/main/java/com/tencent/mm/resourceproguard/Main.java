package com.tencent.mm.resourceproguard;

import com.tencent.mm.androlib.AndrolibException;
import com.tencent.mm.androlib.ApkDecoder;
import com.tencent.mm.androlib.ResourceApkBuilder;
import com.tencent.mm.androlib.res.decoder.ARSCDecoder;
import com.tencent.mm.androlib.res.util.StringUtil;
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
  public static final int ERRNO_USAGE = 2;
  protected static long mRawApkSize;
  protected static String mRunningLocation;
  protected static long mBeginTime;

  /**
   * 是否通过命令行方式设置
   **/
  public boolean mSetSignThroughCmd;
  public boolean mSetMappingThroughCmd;
  public String m7zipPath;
  public String mZipalignPath;
  public String mFinalApkBackPath;

  protected Configuration config;
  protected File mOutDir;

  public static void gradleRun(InputParam inputParam) {
    Main m = new Main();
    m.run(inputParam);
  }

  private void run(InputParam inputParam) {
    synchronized (Main.class) {
      loadConfigFromGradle(inputParam);
      this.mFinalApkBackPath = inputParam.finalApkBackupPath;
      Thread currentThread = Thread.currentThread();
      System.out.printf(
          "\n-->AndResGuard starting! Current thread# id: %d, name: %s\n",
          currentThread.getId(),
          currentThread.getName()
      );
      File finalApkFile = StringUtil.isPresent(inputParam.finalApkBackupPath) ?
          new File(inputParam.finalApkBackupPath)
          : null;

      resourceProguard(
          new File(inputParam.outFolder),
          finalApkFile,
          inputParam.apkPath,
          inputParam.signatureType,
          inputParam.minSDKVersion
      );
      System.out.printf("<--AndResGuard Done! You can find the output in %s\n", mOutDir.getAbsolutePath());
      clean();
    }
  }

  protected void clean() {
    config = null;
    ARSCDecoder.mTableStringsResguard.clear();
    ARSCDecoder.mMergeDuplicatedResCount = 0;
  }

  private void loadConfigFromGradle(InputParam inputParam) {
    try {
      config = new Configuration(inputParam);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected void resourceProguard(
      File outputDir, File outputFile, String apkFilePath, InputParam.SignatureType signatureType) {
    resourceProguard(outputDir, outputFile, apkFilePath, signatureType, 14 /*default min sdk*/);
  }

  protected void resourceProguard(
      File outputDir, File outputFile, String apkFilePath, InputParam.SignatureType signatureType, int minSDKVersoin) {
    File apkFile = new File(apkFilePath);
    if (!apkFile.exists()) {
      System.err.printf("The input apk %s does not exist", apkFile.getAbsolutePath());
      goToError();
    }
    mRawApkSize = FileOperation.getFileSizes(apkFile);
    try {
      ApkDecoder decoder = new ApkDecoder(config, apkFile);
      /* 默认使用V1签名 */
      decodeResource(outputDir, decoder, apkFile);
      buildApk(decoder, apkFile, outputFile, signatureType, minSDKVersoin);
    } catch (Exception e) {
      e.printStackTrace();
      goToError();
    }
  }

  private void decodeResource(File outputFile, ApkDecoder decoder, File apkFile)
      throws AndrolibException, IOException, DirectoryException {
    if (outputFile == null) {
      mOutDir = new File(mRunningLocation, apkFile.getName().substring(0, apkFile.getName().indexOf(".apk")));
    } else {
      mOutDir = outputFile;
    }
    decoder.setOutDir(mOutDir.getAbsoluteFile());
    decoder.decode();
  }

  private void buildApk(
      ApkDecoder decoder, File apkFile, File outputFile, InputParam.SignatureType signatureType, int minSDKVersion)
      throws Exception {
    ResourceApkBuilder builder = new ResourceApkBuilder(config);
    String apkBasename = apkFile.getName();
    apkBasename = apkBasename.substring(0, apkBasename.indexOf(".apk"));
    builder.setOutDir(mOutDir, apkBasename, outputFile);
    System.out.printf("[AndResGuard] buildApk signatureType: %s\n", signatureType);
    switch (signatureType) {
      case SchemaV1:
        builder.buildApkWithV1sign(decoder.getCompressData());
        break;
      case SchemaV2:
        builder.buildApkWithV2sign(decoder.getCompressData(), minSDKVersion);
        break;
    }
  }

  protected void goToError() {
    System.exit(ERRNO_USAGE);
  }
}