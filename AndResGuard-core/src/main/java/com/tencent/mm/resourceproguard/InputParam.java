package com.tencent.mm.resourceproguard;

import java.io.File;
import java.util.ArrayList;

public class InputParam {
    public enum SignatureType {
        SchemaV1, SchemaV2
    }

    public final File              mappingFile;
    public final boolean           use7zip;
    public final boolean           keepRoot;
    public final boolean           useSign;
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
    public final SignatureType     signatureType;

    private InputParam(
        File mappingFile,
        boolean use7zip,
        boolean useSign,
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
        String sevenZipPath,
        SignatureType signatureType
    ) {
        this.mappingFile = mappingFile;
        this.use7zip = use7zip;
        this.useSign = useSign;
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
        this.signatureType = signatureType;
    }

    public static class Builder {
        private File              mappingFile;
        private boolean           use7zip;
        private boolean           useSign;
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
        private SignatureType     signatureType;

        public Builder() {
            use7zip = false;
            keepRoot = false;
            signatureType = SignatureType.SchemaV1;
        }

        public Builder setMappingFile(File mappingFile) {
            this.mappingFile = mappingFile;
            return this;
        }

        public Builder setUse7zip(boolean use7zip) {
            this.use7zip = use7zip;
            return this;
        }

        public Builder setUseSign(boolean useSign) {
            this.useSign = useSign;
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

        public Builder setSignatureType(SignatureType signatureType) {
            this.signatureType = signatureType;
            return this;
        }

        public InputParam create() {
            return new InputParam(
                mappingFile,
                use7zip,
                useSign,
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
                sevenZipPath,
                signatureType
            );
        }
    }
}
