package com.tencent.gradle

/**
 * Created by simsun on 5/13/16.
 */

class BuildInfo {
    def file
    def signConfig
    def packageName

    public BuildInfo(file, sign, packageName) {
        this.file = file
        this.signConfig = sign
        this.packageName = packageName
    }
}
