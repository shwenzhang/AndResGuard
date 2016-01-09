package com.tencent.mm.resourceproguard;

import java.util.ArrayList;

/**
 * Created by sun on 1/9/16.
 */
public class InputParam {
    public final String            mappingFilePath;
    public final boolean           use7zip;
    public final boolean           keepRoot;
    public final ArrayList<String> whiteList;
    public final ArrayList<String> compressFilePattern;
    public final String            apkPath;
    public final String            outFolder;

    private InputParam(
        String mappingFilePath,
        boolean use7zip,
        boolean keepRoot,
        ArrayList<String> whiteList,
        ArrayList<String> compressFilePattern,
        String apkPath,
        String outFolder
    ) {
        this.mappingFilePath = mappingFilePath;
        this.use7zip = use7zip;
        this.keepRoot = keepRoot;
        this.whiteList = whiteList;
        this.compressFilePattern = compressFilePattern;
        this.apkPath = apkPath;
        this.outFolder = outFolder;
    }

    public static class Builder {
        private String mappingFilePath;
        private boolean           use7zip;
        private boolean           keepRoot;
        private ArrayList<String> whiteList;
        private ArrayList<String> compressFilePattern;
        private String apkPath;
        private String outFolder;

        public Builder() {
            use7zip = false;
            keepRoot = false;
        }

        public Builder setMappingFilePath(String mappingFilePath) {
            this.mappingFilePath = mappingFilePath;
            return this;
        }

        public Builder setUse7zip(boolean use7zip) {
            this.use7zip = use7zip;
            return this;
        }

        public Builder setKeepRoot(boolean keepRoot) {
            this.keepRoot = keepRoot;
            return this;
        }

        public Builder setWhiteList(ArrayList<String> whiteList) {
            this.whiteList = whiteList;
            return this;
        }

        public Builder setCompressFilePattern(ArrayList<String> compressFilePattern) {
            this.compressFilePattern = compressFilePattern;
            return this;
        }

        public Builder setApkPath(String apkPath) {
            this.apkPath = apkPath;
            return this;
        }

        public Builder setOutBuilder(String outFolder) {
            this.outFolder = outFolder;
            return this;
        }

        public InputParam create() {
            return new InputParam(
                mappingFilePath,
                use7zip,
                keepRoot,
                whiteList,
                compressFilePattern,
                apkPath,
                outFolder
            );
        }
    }
}
