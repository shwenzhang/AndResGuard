
package com.tencent.mm.androlib;


import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import com.tencent.mm.androlib.res.data.ResPackage;
import com.tencent.mm.androlib.res.decoder.ARSCDecoder;
import com.tencent.mm.androlib.res.decoder.RawARSCDecoder;
import com.tencent.mm.androlib.res.util.ExtFile;
import com.tencent.mm.directory.DirectoryException;
import com.tencent.mm.resourceproguard.Configuration;
import com.tencent.mm.util.FileOperation;
import com.tencent.mm.util.TypedValue;

/**
 * @author shwenzhang
 */
public class ApkDecoder {

    private final Configuration config;
    private ExtFile mApkFile;
    private File    mOutDir;
    private File    mOutTempARSCFile;
    private File    mOutARSCFile;
    private File    mOutResFile;
    private File    mRawResFile;
    private File    mOutTempDir;
    private File    mResMappingFile;
    private HashMap<String, Integer> mCompressData;

    public ApkDecoder(Configuration config) {
        this.config = config;
    }

    public Configuration getConfig() {
        return config;
    }

    public boolean hasResources() throws AndrolibException {
        try {
            return mApkFile.getDirectory().containsFile("resources.arsc");
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    public void setApkFile(File apkFile) {
        mApkFile = new ExtFile(apkFile);
    }

    public void setOutDir(File outDir) throws AndrolibException {
        mOutDir = outDir;
    }

    private void ensureFilePath() throws IOException {
        String destDirectory = mOutDir.getAbsolutePath();
        if (mOutDir.exists()) {
            FileOperation.deleteDir(mOutDir);
            mOutDir.mkdirs();
        }
        String unZipDest = destDirectory + File.separator + TypedValue.UNZIP_FILE_PATH;
        System.out.printf("unziping apk to %s\n", unZipDest);
        mCompressData = FileOperation.unZipAPk(mApkFile.getAbsoluteFile().getAbsolutePath(), unZipDest);
        dealWithCompressConfig();

        //将res混淆成r
        if (!config.mKeepRoot) {
            mOutResFile = new File(mOutDir.getAbsolutePath() + File.separator + TypedValue.RES_FILE_PATH);
        } else {
            mOutResFile = new File(mOutDir.getAbsolutePath() + File.separator + "res");
        }

        //这个需要混淆各个文件夹
        mRawResFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + TypedValue.UNZIP_FILE_PATH + File.separator + "res");
        mOutTempDir = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + TypedValue.UNZIP_FILE_PATH);

        if (!mRawResFile.exists() || !mRawResFile.isDirectory()) {
            throw new IOException("can not found res dir in the apk or it is not a dir");
        }

        mOutTempARSCFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + "resources_temp.arsc");
        mOutARSCFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + "resources.arsc");

        String basename = mApkFile.getName().substring(0, mApkFile.getName().indexOf(".apk"));
        mResMappingFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator
            + TypedValue.RES_MAPPING_FILE + basename + TypedValue.TXT_FILE);
    }

    /**
     * 根据config来修改压缩的值
     */
    private void dealWithCompressConfig() {
        if (config.mUseCompress) {
            HashSet<Pattern> patterns = config.mCompressPatterns;
            if (!patterns.isEmpty()) {
                for (Entry<String, Integer> entry : mCompressData.entrySet()) {
                    String name = entry.getKey();
                    for (Iterator<Pattern> it = patterns.iterator(); it.hasNext(); ) {
                        Pattern p = it.next();
                        if (p.matcher(name).matches()) {
                            mCompressData.put(name, TypedValue.ZIP_DEFLATED);
                        }
                    }

                }
            }
        }
    }

    public HashMap<String, Integer> getCompressData() {
        return mCompressData;
    }

    public File getOutDir() {
        return mOutDir;
    }

    public File getOutResFile() {
        return mOutResFile;
    }

    public File getRawResFile() {
        return mRawResFile;
    }

    public File getOutTempARSCFile() {
        return mOutTempARSCFile;
    }

    public File getOutARSCFile() {
        return mOutARSCFile;
    }

    public File getOutTempDir() {
        return mOutTempDir;
    }

    public File getResMappingFile() {
        return mResMappingFile;
    }


    public void decode() throws AndrolibException, IOException, DirectoryException {
        if (hasResources()) {
            ensureFilePath();
            // read the resources.arsc checking for STORED vs DEFLATE compression
            // this will determine whether we compress on rebuild or not.
            System.out.printf("decoding resources.arsc\n");
            RawARSCDecoder.decode(mApkFile.getDirectory().getFileInput("resources.arsc"));
            ResPackage[] pkgs = ARSCDecoder.decode(mApkFile.getDirectory().getFileInput("resources.arsc"), this);
            ARSCDecoder.write(mApkFile.getDirectory().getFileInput("resources.arsc"), this, pkgs);
        }
    }
}
