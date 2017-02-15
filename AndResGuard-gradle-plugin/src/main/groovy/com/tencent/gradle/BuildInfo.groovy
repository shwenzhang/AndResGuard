package com.tencent.gradle

/**
 * Created by simsun on 5/13/16.
 */

class BuildInfo {
    def file
    def signConfig
    def packageName

    BuildInfo(file, sign, packageName) {
        this.file = file
        this.signConfig = sign
        this.packageName = packageName
    }

    @Override
    String toString() {
        """| file = ${file}
           | packageName = ${packageName}
        """.stripMargin()
    }
}
