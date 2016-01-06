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
        project.task("hello") << {
            println "Hello from AndResGuard Plugin"
        }
    }
}