package com.tencent.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author Sim Sun (sunsj1231@gmail.com)
 */
public class AndResGuardTask extends DefaultTask {
    def configuration

    public AndResGuardExtension() {

    }

    @TaskAction
    def generate() {
        logger.info 'Using this configuration:\n{}', configuration
        Jsonschema2Pojo.generate(configuration)
    }
}