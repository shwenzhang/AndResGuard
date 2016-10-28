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
    public void apply(Project project) {
        project.apply plugin: 'osdetector'
        project.extensions.create('andResGuard', AndResGuardExtension)
        project.extensions.add("sevenzip", new ExecutorExtension("sevenzip"))
        project.extensions.android.productFlavors.all { flavor ->
            createTask(project, flavor)
        }

        project.extensions.android.buildTypes.all { buildType ->
            createTask(project, buildType)
        }

        project.afterEvaluate {
            def ExecutorExtension sevenzip = project.extensions.findByName("sevenzip")
            sevenzip.loadArtifact(project)
        }
    }

    private void createTask(Project project, variant) {
        def typeName = new StringBuffer()
                .append(Character.toUpperCase(variant.name.charAt(0)))
                .append(variant.name[1..-1])
                .toString()

        def task = project.task("resguard${typeName}", type: AndResGuardTask)
        task.dependsOn "assemble${typeName}"
    }
}