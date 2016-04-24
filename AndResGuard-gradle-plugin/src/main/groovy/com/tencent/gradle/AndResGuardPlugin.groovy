package com.tencent.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

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
        project.tasks.create('resguard', AndResGuardSchemaTask)
        project.afterEvaluate {
            def ExecutorExtension sevenzip = project.extensions.findByName("sevenzip")
            sevenzip.loadArtifact(project)
        }
    }
}