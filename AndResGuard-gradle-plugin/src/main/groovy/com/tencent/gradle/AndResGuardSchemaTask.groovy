package com.tencent.gradle

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
public class AndResGuardSchemaTask extends DefaultTask {
    def AndResGuardExtension configuration
    def android
    def releaseApkPaths = []

    AndResGuardSchemaTask() {
        description = 'Assemble Resource Proguard APK'
        group = 'andresguard'
        outputs.upToDateWhen { false }
        project.afterEvaluate {
            configuration = project.andResGuard
            android = project.extensions.android
            android.applicationVariants.all { variant ->
                if (variant.buildType.name == 'release') {
                    this.dependsOn variant.assemble
                    variant.outputs.each { output ->
                        releaseApkPaths << output.outputFile
                    }
                }
            }
            if (!project.plugins.hasPlugin('com.android.application')) {
                throw new GradleException('generateARGApk: Android Application plugin required')
            }
        }
    }

    def useFolder(file) {
        //remove .apk from filename
        def fileName = file.name[0..-5]
        return "${file.parent}/AndResProguard_${fileName}/"
    }

    def getZipAlignPath() {
        return "${android.getSdkDirectory().getAbsolutePath()}/build-tools/${android.buildToolsVersion}/zipalign"
    }

    @TaskAction
    def resuguard() {
        project.logger.info("[AndResGuard]zipaligin: path: " + getZipAlignPath())
        project.logger.info("[AndResGuard]configuartion:$configuration")
        def ExecutorExtension sevenzip = project.extensions.findByName("sevenzip") as ExecutorExtension

        releaseApkPaths.each { path ->
            def String absPath = path.getAbsolutePath()
            InputParam.Builder builder = new InputParam.Builder()
                    .setMappingFile(configuration.mappingFile)
                    .setWhiteList(configuration.whiteList)
                    .setUse7zip(configuration.use7zip)
                    .setMetaName(configuration.metaName)
                    .setKeepRoot(configuration.keepRoot)
                    .setCompressFilePattern(configuration.compressFilePattern)
                    .setZipAlign(getZipAlignPath())
                    .setSevenZipPath(sevenzip.path)
                    .setOutBuilder(useFolder(path))
                    .setApkPath(absPath)
                    .setUseSign(configuration.useSign);

            if (configuration.useSign) {
                builder.setSignFile(android.signingConfigs.release.storeFile)
                        .setKeypass(android.signingConfigs.release.keyPassword)
                        .setStorealias(android.signingConfigs.release.keyAlias)
                        .setStorepass(android.signingConfigs.release.storePassword)
            }
            InputParam inputParam = builder.create();
            Main.gradleRun(inputParam)
        }
    }
}