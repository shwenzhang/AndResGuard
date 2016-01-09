package com.tencent.gradle

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
        println "Using this configuration:\n $configuration"
        String configPath = configuration.configFilePath
        String signPath = android.signingConfigs.release.storeFile
        String mappingPath = configuration.mappingPath
        String keyPass = android.signingConfigs.release.keyPassword
        String storealias = android.signingConfigs.release.keyAlias
        String storePass = android.signingConfigs.release.storePassword
        Main.gradleRun(configPath, signPath, mappingPath, keyPass, storealias, storePass, releaseFolder, releaseApkPath)
    }
}