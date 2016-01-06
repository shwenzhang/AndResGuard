package com.tencent.gradle

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

    AssembleARGApkTask() {
        description = 'Assemble Proguard APK'
        group = 'Assemble'

        outputs.upToDateWhen { false }

        project.afterEvaluate {
            configuration = project.jsonSchema2Pojo
            configuration.targetDirectory = configuration.targetDirectory ?:
                    project.file("${project.buildDir}/generated-apk/ARG")

            if (project.plugins.hasPlugin('com.android.application')) {
                configureEnv()
            } else {
                throw new GradleException('generateARGApk: Android Application plugin required')
            }
            outputs.dir configuration.targetDirectory
        }
    }

    def configureEnv() {
        def android = project.extensions.android
        android.sourceSets.main.java.srcDirs += [ configuration.targetDirectory ]
    }

    @TaskAction
    def generate() {
        logger.info 'Using this configuration:\n{}', configuration
        Jsonschema2Pojo.generate(configuration)
    }
}