package com.tencent.gradle

import com.tencent.mm.resourceproguard.InputParam
import com.tencent.mm.resourceproguard.Main
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author Sim Sun (sunsj1231@gmail.com)
 */
public class AndResGuardTask extends DefaultTask {
    def AndResGuardExtension configuration
    def android
    def buildConfigs = []

    AndResGuardTask() {
        description = 'Assemble Resource Proguard APK'
        group = 'andresguard'
        outputs.upToDateWhen { false }
        android = project.extensions.android
        configuration = project.andResGuard
        android.applicationVariants.all { variant ->
            variant.outputs.each { output ->
                // resguard's length is 8, the postfix of name is the current buildType.
                if (this.name[8..-1].equalsIgnoreCase(variant.buildType.name as String)) {
                    buildConfigs << new BuildInfo(
                            output.outputFile,
                            variant.apkVariantData.variantConfiguration.signingConfig,
                            variant.apkVariantData.variantConfiguration.applicationId
                    )
                }
            }
        }
        if (!project.plugins.hasPlugin('com.android.application')) {
            throw new GradleException('generateARGApk: Android Application plugin required')
        }
    }

    static def useFolder(file) {
        //remove .apk from filename
        def fileName = file.name[0..-5]
        return "${file.parent}/AndResGuard_${fileName}/"
    }

    def getZipAlignPath() {
        return "${android.getSdkDirectory().getAbsolutePath()}/build-tools/${android.buildToolsVersion}/zipalign"
    }

    @TaskAction
    def run() {
        project.logger.error("[AndResGuard] zipaligin path: " + getZipAlignPath())
        project.logger.info("[AndResGuard] configuartion:$configuration")
        def ExecutorExtension sevenzip = project.extensions.findByName("sevenzip") as ExecutorExtension

        buildConfigs.each { config ->
            def String absPath = config.file.getAbsolutePath()
            def signConfig = config.signConfig
            def String packageName = config.packageName
            ArrayList<String> whiteListFullName = new ArrayList<>();
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
                    .setKeepRoot(configuration.keepRoot)
                    .setCompressFilePattern(configuration.compressFilePattern)
                    .setZipAlign(getZipAlignPath())
                    .setSevenZipPath(sevenzip.path)
                    .setOutBuilder(useFolder(config.file))
                    .setApkPath(absPath)
                    .setUseSign(configuration.useSign);

            if (configuration.useSign) {
                if (signConfig == null) {
                    throw new GradleException("can't the get signConfig for release build")
                }
                builder.setSignFile(signConfig.storeFile)
                        .setKeypass(signConfig.keyPassword)
                        .setStorealias(signConfig.keyAlias)
                        .setStorepass(signConfig.storePassword)
            }
            InputParam inputParam = builder.create();
            Main.gradleRun(inputParam)
        }
        buildConfigs = []
    }
}