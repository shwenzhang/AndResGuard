package com.tencent.gradle

/**
 * The configuration properties.
 *
 * @author sim sun (sunsj1231@gmail.com)
 */

class AndResGuardExtension {

  File mappingFile
  boolean use7zip
  boolean useSign
  String metaName
  boolean keepRoot
  boolean mergeDuplicatedRes
  Iterable<String> whiteList
  Iterable<String> compressFilePattern
  String finalApkBackupPath
  String digestalg
  String sourceApk
  String sourceBuildType
  String sourceFlavor

  AndResGuardExtension() {
    use7zip = false
    useSign = false
    metaName = "META-INF"
    keepRoot = false
    mergeDuplicatedRes = false
    whiteList = []
    compressFilePattern = []
    mappingFile = null
    finalApkBackupPath = null
    digestalg = null
    sourceApk = null
    sourceBuildType = null
    sourceFlavor = null
  }

  Iterable<String> getCompressFilePattern() {
    return compressFilePattern
  }

  File getMappingFile() {
    return mappingFile
  }

  boolean getUse7zip() {
    return use7zip
  }

  boolean getUseSign() {
    return useSign
  }

  String getMetaName() {
    return metaName
  }

  boolean getKeepRoot() {
    return keepRoot
  }

  boolean getMergeDuplicatedRes() {
    return mergeDuplicatedRes
  }

  Iterable<String> getWhiteList() {
    return whiteList
  }

  String getFinalApkBackupPath() {
    return finalApkBackupPath
  }

  String getDigestalg() {
    return digestalg
  }

  String getSourceApk() {
    return sourceApk
  }

  String getSourceBuildType() {
    return sourceBuildType
  }

  String getSourceFlavor() {
    return sourceFlavor
  }

  @Override
  String toString() {
    """| use7zip = ${use7zip}
           | useSign = ${useSign}
           | metaName = ${metaName}
           | keepRoot = ${keepRoot}
           | mergeDuplicatedRes = ${mergeDuplicatedRes}
           | whiteList = ${whiteList}
           | compressFilePattern = ${compressFilePattern}
           | finalApkBackupPath = ${finalApkBackupPath}
           | digstalg = ${digestalg}
           | sourceApk = ${sourceApk}
           | sourceBuildType = ${sourceBuildType}
           | sourceFlavor = ${sourceFlavor}
        """.stripMargin()
  }
}