package com.tencent.mm.androlib;

import com.tencent.mm.androlib.res.data.ResPackage;
import com.tencent.mm.androlib.res.decoder.ARSCDecoder;
import com.tencent.mm.androlib.res.decoder.RawARSCDecoder;
import com.tencent.mm.androlib.res.util.ExtFile;
import com.tencent.mm.directory.DirectoryException;
import com.tencent.mm.resourceproguard.Configuration;
import com.tencent.mm.util.FileOperation;
import com.tencent.mm.util.TypedValue;
import com.tencent.mm.util.Utils;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * @author shwenzhang
 */
public class ApkDecoder {

  final HashSet<Path> mRawResourceFiles = new HashSet<>();
  private final Configuration config;
  private final ExtFile apkFile;
  private File mOutDir;
  private File mOutTempARSCFile;
  private File mOutARSCFile;
  private File mOutResFile;
  private File mRawResFile;
  private File mOutTempDir;
  private File mResMappingFile;
  private HashMap<String, Integer> mCompressData;

  public ApkDecoder(Configuration config, File apkFile) {
    this.config = config;
    this.apkFile = new ExtFile(apkFile);
  }

  private void copyOtherResFiles() throws IOException {
    if (mRawResourceFiles.isEmpty()) {
      return;
    }
    Path resPath = mRawResFile.toPath();
    Path destPath = mOutResFile.toPath();

    for (Path path : mRawResourceFiles) {
      Path relativePath = resPath.relativize(path);
      Path dest = destPath.resolve(relativePath);

      System.out.printf("copy res file not in resources.arsc file:%s\n", relativePath.toString());
      FileOperation.copyFileUsingStream(path.toFile(), dest.toFile());
    }
  }

  public void removeCopiedResFile(Path key) {
    mRawResourceFiles.remove(key);
  }

  public Configuration getConfig() {
    return config;
  }

  public boolean hasResources() throws AndrolibException {
    try {
      return apkFile.getDirectory().containsFile("resources.arsc");
    } catch (DirectoryException ex) {
      throw new AndrolibException(ex);
    }
  }

  private void ensureFilePath() throws IOException {
    Utils.cleanDir(mOutDir);

    String unZipDest = new File(mOutDir, TypedValue.UNZIP_FILE_PATH).getAbsolutePath();
    System.out.printf("unziping apk to %s\n", unZipDest);
    mCompressData = FileOperation.unZipAPk(apkFile.getAbsoluteFile().getAbsolutePath(), unZipDest);
    dealWithCompressConfig();
    //将res混淆成r
    if (!config.mKeepRoot) {
      mOutResFile = new File(mOutDir.getAbsolutePath() + File.separator + TypedValue.RES_FILE_PATH);
    } else {
      mOutResFile = new File(mOutDir.getAbsolutePath() + File.separator + "res");
    }

    //这个需要混淆各个文件夹
    mRawResFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath()
                           + File.separator
                           + TypedValue.UNZIP_FILE_PATH
                           + File.separator
                           + "res");
    mOutTempDir = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + TypedValue.UNZIP_FILE_PATH);

    //这里纪录原始res目录的文件
    Files.walkFileTree(mRawResFile.toPath(), new ResourceFilesVisitor());

    if (!mRawResFile.exists() || !mRawResFile.isDirectory()) {
      throw new IOException("can not found res dir in the apk or it is not a dir");
    }

    mOutTempARSCFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + "resources_temp.arsc");
    mOutARSCFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath() + File.separator + "resources.arsc");

    String basename = apkFile.getName().substring(0, apkFile.getName().indexOf(".apk"));
    mResMappingFile = new File(mOutDir.getAbsoluteFile().getAbsolutePath()
                               + File.separator
                               + TypedValue.RES_MAPPING_FILE
                               + basename
                               + TypedValue.TXT_FILE);
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

  public void setOutDir(File outDir) throws AndrolibException {
    mOutDir = outDir;
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
      RawARSCDecoder.decode(apkFile.getDirectory().getFileInput("resources.arsc"));
      ResPackage[] pkgs = ARSCDecoder.decode(apkFile.getDirectory().getFileInput("resources.arsc"), this);

      //把没有纪录在resources.arsc的资源文件也拷进dest目录
      copyOtherResFiles();

      ARSCDecoder.write(apkFile.getDirectory().getFileInput("resources.arsc"), this, pkgs);
    }
  }

  class ResourceFilesVisitor extends SimpleFileVisitor<Path> {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      mRawResourceFiles.add(file);
      return FileVisitResult.CONTINUE;
    }
  }
}
