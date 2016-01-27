package com.tencent.mm.resourceproguard;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by sun on 1/9/16.
 */
public class InputParam {
    public final File              mappingFile;
    public final boolean           use7zip;
    public final boolean           keepRoot;
    public final String            metaName;
    public final ArrayList<String> whiteList;
    public final ArrayList<String> compressFilePattern;
    public final String            apkPath;
    public final String            outFolder;
    public final File              signFile;
    public final String            keypass;
    public final String            storealias;
    public final String            storepass;
    public final String            zipAlignPath;
    public final String            sevenZipPath;

    private InputParam(
        File mappingFile,
        boolean use7zip,
        boolean keepRoot,
        ArrayList<String> whiteList,
        ArrayList<String> compressFilePattern,
        String apkPath,
        String outFolder,
        File signFile,
        String keypass,
        String storealias,
        String storepass,
        String metaName,
        String zipAlignPath,
        String sevenZipPath
    ) {
        this.mappingFile = mappingFile;
        this.use7zip = use7zip;
        this.keepRoot = keepRoot;
        this.whiteList = whiteList;
        this.compressFilePattern = compressFilePattern;
        this.apkPath = apkPath;
        this.outFolder = outFolder;
        this.signFile = signFile;
        this.keypass = keypass;
        this.storealias = storealias;
        this.storepass = storepass;
        this.metaName = metaName;
        this.zipAlignPath = zipAlignPath;
        this.sevenZipPath = sevenZipPath;
    }

    public static class Builder {
        private File              mappingFile;
        private boolean           use7zip;
        private boolean           keepRoot;
        private ArrayList<String> whiteList;
        private ArrayList<String> compressFilePattern;
        private String            apkPath;
        private String            outFolder;
        private File              signFile;
        private String            keypass;
        private String            storealias;
        private String            storepass;
        private String            metaName;
        private String            zipAlignPath;
        private String            sevenZipPath;

        public Builder() {
            use7zip = false;
            keepRoot = false;
        }

        public Builder setMappingFile(File mappingFile) {
            this.mappingFile = mappingFile;
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

        public Builder setSignFile(File signFile) {
            this.signFile = signFile;
            return this;
        }

        public Builder setKeypass(String keypass) {
            this.keypass = keypass;
            return this;
        }

        public Builder setStorealias(String storealias) {
            this.storealias = storealias;
            return this;
        }

        public Builder setStorepass(String storepass) {
            this.storepass = storepass;
            return this;
        }

        public Builder setMetaName(String metaName) {
            this.metaName = metaName;
            return this;
        }

        public Builder setZipAlign(String zipAlignPath) {
            this.zipAlignPath = zipAlignPath;
            return this;
        }

        public Builder setSevenZipPath(String sevenZipPath) {
            this.sevenZipPath = sevenZipPath;
            return this;
        }

        public InputParam create() {
            return new InputParam(
                mappingFile,
                use7zip,
                keepRoot,
                whiteList,
                compressFilePattern,
                apkPath,
                outFolder,
                signFile,
                keypass,
                storealias,
                storepass,
                metaName,
                zipAlignPath,
                sevenZipPath
            );
        }
    }
}
