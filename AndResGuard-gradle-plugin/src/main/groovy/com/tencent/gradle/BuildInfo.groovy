package com.tencent.gradle

/**
 * Created by simsun on 5/13/16.
 */

class BuildInfo {
    def file
    def signConfig

    public BuildInfo(file, sign) {
        this.file = file;
        this.signConfig = sign;
    }
}
