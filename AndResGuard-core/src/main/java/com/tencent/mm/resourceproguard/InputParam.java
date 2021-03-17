package com.tencent.mm.resourceproguard;

import com.tencent.mm.androlib.res.util.StringUtil;
import java.io.File;
import java.util.ArrayList;

public class InputParam {

  public final File mappingFile;
  public final boolean use7zip;
  public final boolean keepRoot;
  public final boolean mergeDuplicatedRes;
  public final boolean useSign;
  public final String metaName;
  public final String fixedResName;
  public final ArrayList<String> whiteList;
  public final ArrayList<String> compressFilePattern;
  public final String apkPath;
  public final String outFolder;
  public final File signFile;
  public final String keypass;
  public final String storealias;
  public final String storepass;
  public final String zipAlignPath;
  public final String sevenZipPath;
  public final SignatureType signatureType;
  public final String finalApkBackupPath;
  public final String digestAlg;
  public final int minSDKVersion;
  public final int targetSDKVersion;

  private InputParam(
      File mappingFile,
      boolean use7zip,
      boolean useSign,
      boolean keepRoot,
      boolean mergeDuplicatedRes,
      ArrayList<String> whiteList,
      ArrayList<String> compressFilePattern,
      String apkPath,
      String outFolder,
      File signFile,
      String keypass,
      String storealias,
      String storepass,
      String metaName,
      String fixedResName,
      String zipAlignPath,
      String sevenZipPath,
      SignatureType signatureType,
      String finalApkBackupPath,
      String digestAlg,
      int minSDKVersion,
      int targetSDKVersion) {

    this.mappingFile = mappingFile;
    this.use7zip = use7zip;
    this.useSign = useSign;
    this.keepRoot = keepRoot;
    this.mergeDuplicatedRes = mergeDuplicatedRes;
    this.whiteList = whiteList;
    this.compressFilePattern = compressFilePattern;
    this.apkPath = apkPath;
    this.outFolder = outFolder;
    this.signFile = signFile;
    this.keypass = keypass;
    this.storealias = storealias;
    this.storepass = storepass;
    this.metaName = metaName;
    this.fixedResName = fixedResName;
    this.zipAlignPath = zipAlignPath;
    this.sevenZipPath = sevenZipPath;
    this.signatureType = signatureType;
    this.finalApkBackupPath = finalApkBackupPath;
    this.digestAlg = digestAlg;
    this.minSDKVersion = minSDKVersion;
    this.targetSDKVersion = targetSDKVersion;
  }

  public enum SignatureType {
    SchemaV1, SchemaV2, SchemaV3
  }

  public static class Builder {

    private File mappingFile;
    private boolean use7zip;
    private boolean useSign;
    private boolean keepRoot;
    private boolean mergeDuplicatedRes;
    private ArrayList<String> whiteList;
    private ArrayList<String> compressFilePattern;
    private String apkPath;
    private String outFolder;
    private File signFile;
    private String keypass;
    private String storealias;
    private String storepass;
    private String metaName;
    private String fixedResName;
    private String zipAlignPath;
    private String sevenZipPath;
    private SignatureType signatureType;
    private String finalApkBackupPath;
    private String digestAlg;
    private int minSDKVersion;
    private int targetSDKVersion;

    public Builder() {
      use7zip = false;
      keepRoot = false;
      signatureType = SignatureType.SchemaV1;
    }

    public Builder setMappingFile(File mappingFile) {
      this.mappingFile = mappingFile;
      return this;
    }

    public Builder setUse7zip(boolean use7zip) {
      this.use7zip = use7zip;
      return this;
    }

    public Builder setUseSign(boolean useSign) {
      this.useSign = useSign;
      return this;
    }

    public Builder setKeepRoot(boolean keepRoot) {
      this.keepRoot = keepRoot;
      return this;
    }

    public Builder setMergeDuplicatedRes(boolean mergeDuplicatedRes) {
      this.mergeDuplicatedRes = mergeDuplicatedRes;
      return this;
    }

    public Builder setWhiteList(ArrayList<String> whiteList) {
      this.whiteList = whiteList;
      return this;
    }

    public Builder setCompressFilePattern(ArrayList<String> compressFilePattern) {
      if (compressFilePattern.contains(Configuration.ASRC_FILE)) {
        System.out.printf("[Warning] compress %s will prevent optimization at runtime",
            Configuration.ASRC_FILE);
      }
      this.compressFilePattern = compressFilePattern;
      return this;
    }

    public Builder setApkPath(String apkPath) {
      this.apkPath = apkPath;
      return this;
    }

    public Builder setOutBuilder(String outFolder) {
      this.outFolder = outFolder;
      return this;
    }

    public Builder setSignFile(File signFile) {
      this.signFile = signFile;
      return this;
    }

    public Builder setKeypass(String keypass) {
      this.keypass = keypass;
      return this;
    }

    public Builder setStorealias(String storealias) {
      this.storealias = storealias;
      return this;
    }

    public Builder setStorepass(String storepass) {
      this.storepass = storepass;
      return this;
    }

    public Builder setMetaName(String metaName) {
      this.metaName = metaName;
      return this;
    }

    public Builder setFixedResName(String fixedResName) {
      this.fixedResName = fixedResName;
      return this;
    }

    public Builder setZipAlign(String zipAlignPath) {
      this.zipAlignPath = zipAlignPath;
      return this;
    }

    public Builder setSevenZipPath(String sevenZipPath) {
      this.sevenZipPath = sevenZipPath;
      return this;
    }

    public Builder setSignatureType(SignatureType signatureType) {
      this.signatureType = signatureType;
      return this;
    }

    public Builder setFinalApkBackupPath(String finalApkBackupPath) {
      this.finalApkBackupPath = finalApkBackupPath;
      return this;
    }

    public Builder setDigestAlg(String digestAlg) {
      if (StringUtil.isPresent(digestAlg)) {
        this.digestAlg = digestAlg;
      } else {
        this.digestAlg = Configuration.DEFAULT_DIGEST_ALG;
      }

      return this;
    }

    public Builder setMinSDKVersion(int minSDKVersion) {
      this.minSDKVersion = minSDKVersion;
      return this;
    }

    public Builder setTargetSDKVersion(int targetSDKVersion) {
      this.targetSDKVersion = targetSDKVersion;
      return this;
    }

    public InputParam create() {
      if (targetSDKVersion >= 30) {
        // Targeting R+ (version 30 and above) requires the resources.arsc of installed APKs
        // to be stored uncompressed and aligned on a 4-byte boundary
        this.compressFilePattern.remove(Configuration.ASRC_FILE);
        System.out.printf("[Warning] Remove resources.arsc from the compressPattern. (%s)\n",
            this.compressFilePattern);
      }

      return new InputParam(mappingFile,
          use7zip,
          useSign,
          keepRoot,
          mergeDuplicatedRes,
          whiteList,
          compressFilePattern,
          apkPath,
          outFolder,
          signFile,
          keypass,
          storealias,
          storepass,
          metaName,
          fixedResName,
          zipAlignPath,
          sevenZipPath,
          signatureType,
          finalApkBackupPath,
          digestAlg,
          minSDKVersion,
          targetSDKVersion
      );
    }
  }
}
