package com.tencent.gradle

import com.tencent.mm.androlib.res.util.StringUtil
import com.tencent.mm.directory.PathNotExist
import com.tencent.mm.resourceproguard.InputParam
import com.tencent.mm.resourceproguard.Main
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author Sim Sun (sunsj1231@gmail.com)
 */
class AndResGuardTask extends DefaultTask {
  AndResGuardExtension configuration
  def android
  def buildConfigs = []

  AndResGuardTask() {
    description = 'Assemble Resource Proguard APK'
    group = 'andresguard'
    outputs.upToDateWhen { false }
    android = project.extensions.android
    configuration = project.andResGuard

    if (StringUtil.isPresent(configuration.digestalg) && !configuration.digestalg.contains('-')) {
      throw new RuntimeException("Plz add - in your digestalg, such as SHA-1 SHA-256")
    }

    android.applicationVariants.all { variant ->
      variant.outputs.each { output ->
        // remove "resguard"
        String variantName = this.name["resguard".length()..-1]
        if (variantName.equalsIgnoreCase(variant.buildType.name as String) || isTargetFlavor(variantName,
            variant.productFlavors, variant.buildType.name) ||
            variantName.equalsIgnoreCase(AndResGuardPlugin.USE_APK_TASK_NAME)) {

          def outputFile = null
          try {
            if (variant.metaClass.respondsTo(variant, "getPackageApplicationProvider")) {
              outputFile = new File(variant.packageApplicationProvider.get().outputDirectory, output.outputFileName)
            }
          } catch (Exception ignore) {
            // no-op
          } finally {
            outputFile = outputFile ?: output.outputFile
          }

          buildConfigs << new BuildInfo(
              outputFile,
              variant.variantData.variantConfiguration.signingConfig,
              variant.variantData.variantConfiguration.applicationId,
              variant.buildType.name,
              variant.productFlavors,
              variantName,
              variant.mergedFlavor.minSdkVersion.apiLevel)
        }
      }
    }
    if (!project.plugins.hasPlugin('com.android.application')) {
      throw new GradleException('generateARGApk: Android Application plugin required')
    }
  }

  static isTargetFlavor(variantName, flavors, buildType) {
    if (flavors.size() > 0) {
      String flavor = flavors.get(0).name
      return variantName.equalsIgnoreCase(flavor) || variantName.equalsIgnoreCase([flavors.collect {it.name}.join(""), buildType].join(""))
    }
    return false
  }

  static useFolder(file) {
    //remove .apk from filename
    def fileName = file.name[0..-5]
    return "${file.parent}/AndResGuard_${fileName}/"
  }

  def getZipAlignPath() {
    return "${android.getSdkDirectory().getAbsolutePath()}/build-tools/${android.buildToolsVersion}/zipalign"
  }

  @TaskAction
  run() {
    project.logger.info("[AndResGuard] configuartion:$configuration")
    project.logger.info("[AndResGuard] BuildConfigs:$buildConfigs")

    buildConfigs.each { config ->
      if (config.taskName == AndResGuardPlugin.USE_APK_TASK_NAME) {
        if (StringUtil.isBlank(configuration.sourceApk) || !new File(configuration.sourceApk).exists()) {
          throw new PathNotExist("Original APK not existed for " + AndResGuardPlugin.USE_APK_TASK_NAME)
        }
        if (config.flavors.productFlavors.size() > 0 && StringUtil.isBlank(configuration.sourceFlavor)) {
          throw new RuntimeException("Must setup sourceFlavor when flavors exist in build.gradle")
        }
        if (StringUtil.isBlank(configuration.sourceBuildType)) {
          throw new RuntimeException("Must setup sourceBuildType when flavors exist in build.gradle")
        }
        if (config.buildType == configuration.sourceBuildType) {
          if (StringUtil.isBlank(configuration.sourceFlavor) || (StringUtil.isPresent(configuration.sourceFlavor) &&
              config.flavors.size() >
              0 &&
              config.flavors.get(0).name ==
              configuration.sourceFlavor)) {
            RunGradleTask(config, configuration.sourceApk, config.minSDKVersion)
          }
        }
      } else {
        if (config.file == null || !config.file.exists()) {
          throw new PathNotExist("Original APK not existed")
        }
        RunGradleTask(config, config.file.getAbsolutePath(), config.minSDKVersion)
      }
    }
  }

  def RunGradleTask(config, String absPath, int minSDKVersion) {
    def signConfig = config.signConfig
    String packageName = config.packageName
    ArrayList<String> whiteListFullName = new ArrayList<>()
    ExecutorExtension sevenzip = project.extensions.findByName("sevenzip") as ExecutorExtension
    configuration.whiteList.each { res ->
      if (res.startsWith("R")) {
        whiteListFullName.add(packageName + "." + res)
      } else {
        whiteListFullName.add(res)
      }
    }

    InputParam.Builder builder = new InputParam.Builder()
        .setMappingFile(configuration.mappingFile)
        .setWhiteList(whiteListFullName)
        .setUse7zip(configuration.use7zip)
        .setMetaName(configuration.metaName)
        .setFixedResName(configuration.fixedResName)
        .setKeepRoot(configuration.keepRoot)
        .setMergeDuplicatedRes(configuration.mergeDuplicatedRes)
        .setCompressFilePattern(configuration.compressFilePattern)
        .setZipAlign(getZipAlignPath())
        .setSevenZipPath(sevenzip.path)
        .setOutBuilder(useFolder(config.file))
        .setApkPath(absPath)
        .setUseSign(configuration.useSign)
        .setDigestAlg(configuration.digestalg)
        .setMinSDKVersion(minSDKVersion)

    if (configuration.finalApkBackupPath != null && configuration.finalApkBackupPath.length() > 0) {
      builder.setFinalApkBackupPath(configuration.finalApkBackupPath)
    } else {
      builder.setFinalApkBackupPath(absPath)
    }

    if (configuration.useSign) {
      if (signConfig == null) {
        throw new GradleException("can't the get signConfig for release build")
      }
      builder.setSignFile(signConfig.storeFile)
          .setKeypass(signConfig.keyPassword)
          .setStorealias(signConfig.keyAlias)
          .setStorepass(signConfig.storePassword)
      if (signConfig.hasProperty('v2SigningEnabled') && signConfig.v2SigningEnabled) {
        builder.setSignatureType(InputParam.SignatureType.SchemaV2)
      }
    }
    InputParam inputParam = builder.create()
    Main.gradleRun(inputParam)
  }
}