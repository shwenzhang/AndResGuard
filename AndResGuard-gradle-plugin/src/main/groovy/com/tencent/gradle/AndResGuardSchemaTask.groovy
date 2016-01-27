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
    def configuration
    def android
    String releaseApkPath
    String releaseFolder

    AndResGuardSchemaTask() {
        description = 'Assemble Proguard APK'
        group = 'andresguard'
        outputs.upToDateWhen { false }
        project.afterEvaluate {
            configuration = project.andResGuard
            android = project.extensions.android
            android.applicationVariants.all { variant ->
                println variant.buildType.name
                if (variant.buildType.name == 'release') {
                    this.dependsOn variant.assemble
                    variant.outputs.each { output ->
                        releaseApkPath = output.outputFile
                        releaseFolder = "${output.outputFile.parent}/AndResProguard/"
                    }
                }
            }
            if (!project.plugins.hasPlugin('com.android.application')) {
                throw new GradleException('generateARGApk: Android Application plugin required')
            }
        }
    }

    @TaskAction
    def generate() {
        print configuration
        InputParam inputParam = new InputParam.Builder()
                .setSignFile(android.signingConfigs.release.storeFile)
                .setKeypass(android.signingConfigs.release.keyPassword)
                .setStorealias(android.signingConfigs.release.keyAlias)
                .setStorepass(android.signingConfigs.release.storePassword)
                .setMappingFile(configuration.mappingFile)
                .setWhiteList(configuration.whiteList)
                .setUse7zip(configuration.use7zip)
                .setMetaName(configuration.metaName)
                .setKeepRoot(configuration.keepRoot)
                .setCompressFilePattern(configuration.compressFilePattern)
                .setZipAlign(configuration.zipAlignPath)
                .setSevenZipPath(configuration.sevenZipPath)
                .setOutBuilder(releaseFolder)
                .setApkPath(releaseApkPath)
                .create();
        Main.gradleRun(inputParam)
    }
}