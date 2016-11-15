package com.tencent.gradle

/**
 * The configuration properties.
 *
 * @author sim sun (sunsj1231@gmail.com)
 */

public class AndResGuardExtension {

    File mappingFile;
    boolean use7zip;
    boolean useSign;
    String metaName;
    boolean keepRoot;
    Iterable<String> whiteList;
    Iterable<String> compressFilePattern;

    public AndResGuardExtension() {
        use7zip = false
        useSign = false
        metaName = "META-INF"
        keepRoot = false
        whiteList = []
        compressFilePattern = []
        mappingFile = null
    }

    Iterable<String> getCompressFilePattern() {
        return compressFilePattern
    }

    File getMappingFile() {
        return mappingFile
    }

    boolean getUse7zip() {
        return use7zip
    }

    boolean getUseSign() {
        return useSign
    }

    String getMetaName() {
        return metaName
    }

    boolean getKeepRoot() {
        return keepRoot
    }

    Iterable<String> getWhiteList() {
        return whiteList
    }

    @Override
    public String toString() {
        """| use7zip = ${use7zip}
           | useSign = ${useSign}
           | metaName = ${metaName}
           | keepRoot = ${keepRoot}
           | whiteList = ${whiteList}
           | compressFilePattern = ${compressFilePattern}
        """.stripMargin()
    }
}