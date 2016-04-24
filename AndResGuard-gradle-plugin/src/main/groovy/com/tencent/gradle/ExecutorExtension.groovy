package com.tencent.gradle

import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency

public class ExecutorExtension implements Named {

    private final String name

    private String artifact
    private String path

    public ExecutorExtension(String name) {
        this.name = name
    }

    @Override
    public String getName() {
        return name
    }

    /**
     * Specifies an artifact spec for downloading the executable from
     * repositories. spec format: '<groupId>:<artifactId>:<version>'
     */
    public setArtifact(String spec) {
        this.artifact = spec
    }

    /**
     * Specifies a local path.
     */
    public setPath(String path) {
        this.path = path
    }

    public String getArtifact() {
        return artifact
    }

    public String getPath() {
        return path
    }

    void loadArtifact(Project project) {
        if (path == null && artifact != null) {
            Configuration config = project.configurations.create("AndResGuardLocatorSevenZip") {
                visible = false
                transitive = false
                extendsFrom = []
            }
            def groupId, artifactId, version

            (groupId, artifactId, version) = this.artifact.split(":")
            def notation = [group     : groupId,
                            name      : artifactId,
                            version   : version,
                            classifier: project.osdetector.classifier,
                            ext       : 'exe']

            project.logger.info("[AndResGuard]Resolving artifact: ${notation}")
            Dependency dep = project.dependencies.add(config.name, notation)
            File file = config.fileCollection(dep).singleFile
            if (!file.canExecute() && !file.setExecutable(true)) {
                throw new GradleException("Cannot set ${file} as executable")
            }
            project.logger.info("[AndResGuard]Resolved artifact: ${file}")
            this.path = file.path
        }
    }
}

