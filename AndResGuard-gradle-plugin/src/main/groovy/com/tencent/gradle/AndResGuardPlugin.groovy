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
        project.extensions.create('andResGuard', AndResGuardExtension)
        project.tasks.create('generate', AndResGuardSchemaTask)
    }
}