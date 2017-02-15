package com.tencent.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers the plugin's tasks.
 *
 * @author sim sun (sunsj1231@gmail.com)
 */

class AndResGuardPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.apply plugin: 'com.google.osdetector'
        project.extensions.create('andResGuard', AndResGuardExtension)
        project.extensions.add("sevenzip", new ExecutorExtension("sevenzip"))

        project.afterEvaluate {
            def android = project.extensions.android

            android.applicationVariants.all { variant ->
                def variantName = variant.name.capitalize()
                createTask(project, variantName)
            }

            android.buildTypes.all { buildType ->
                def buildTypeName = buildType.name.capitalize()
                createTask(project, buildTypeName)
            }

            android.productFlavors.all { flavor ->
                def flavorName = flavor.name.capitalize()
                createTask(project, flavorName)
            }

            project.extensions.findByName("sevenzip").loadArtifact(project)
        }
    }

    private static void createTask(Project project, variantName) {
        def taskName = "resguard${variantName}"
        if (project.tasks.findByPath(taskName) == null) {
            def task = project.task(taskName, type: AndResGuardTask)
            task.dependsOn "assemble${variantName}"
        }
    }
}