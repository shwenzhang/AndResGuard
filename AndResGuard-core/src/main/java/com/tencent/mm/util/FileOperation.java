package com.tencent.mm.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class FileOperation {
    private static final int BUFFER = 8192;

    public static final boolean fileExists(String filePath) {
        if (filePath == null) {
            return false;
        }

        File file = new File(filePath);
        if (file.exists())
            return true;
        return false;
    }

    public static final boolean deleteFile(String filePath) {
        if (filePath == null) {
            return true;
        }

        File file = new File(filePath);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    public static long getlist(File f) {
        if (f == null || (!f.exists())) {
            return 0;
        }
        if (!f.isDirectory()) {
            return 1;
        }
        long size;
        File flist[] = f.listFiles();
        size = flist.length;
        for (int i = 0; i < flist.length; i++) {
            if (flist[i].isDirectory()) {
                size = size + getlist(flist[i]);
                size--;
            }
        }
        return size;
    }

    public static long getFileSizes(File f) {
        long size = 0;
        if (f.exists() && f.isFile()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(f);
                size = fis.available();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (fis != null) {
                        fis.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return size;
    }

    public static final boolean deleteDir(File file) {
        if (file == null || (!file.exists())) {
            return false;
        }
        if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            File files[] = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                deleteDir(files[i]);
            }
        }
        file.delete();
        return true;
    }

    public static void copyFileUsingStream(File source, File dest) throws IOException {
        FileInputStream is = null;
        FileOutputStream os = null;
        File parent = dest.getParentFile();
        if (parent != null && (!parent.exists())) {
            parent.mkdirs();
        }
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest, false);

            byte[] buffer = new byte[BUFFER];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            if (is != null) {
                is.close();
            }
            if (os != null) {
                os.close();
            }
        }
    }

    public static boolean checkDirectory(String dir) {
        File dirObj = new File(dir);
        deleteDir(dirObj);

        if (!dirObj.exists()) {
            dirObj.mkdirs();
        }
        return true;
    }

    public static File checkFile(String dir) {
        deleteFile(dir);
        File file = new File(dir);
        try {
            file.createNewFile();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return file;
    }


    @SuppressWarnings("rawtypes")
    public static HashMap<String, Integer> unZipAPk(String fileName, String filePath) throws IOException {
        checkDirectory(filePath);
        ZipFile zipFile = new ZipFile(fileName);
        Enumeration emu = zipFile.entries();
        HashMap<String, Integer> compress = new HashMap<>();
        while (emu.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) emu.nextElement();
            if (entry.isDirectory()) {
                new File(filePath, entry.getName()).mkdirs();
                continue;
            }
            BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));

            File file = new File(filePath + File.separator + entry.getName());

            File parent = file.getParentFile();
            if (parent != null && (!parent.exists())) {
                parent.mkdirs();
            }
            //要用linux的斜杠
            String compatibaleresult = entry.getName();
            if (compatibaleresult.contains("\\")) {
                compatibaleresult = compatibaleresult.replace("\\", "/");
            }
            compress.put(compatibaleresult, entry.getMethod());
            FileOutputStream fos = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER);

            byte[] buf = new byte[BUFFER];
            int len;
            while ((len = bis.read(buf, 0, BUFFER)) != -1) {
                fos.write(buf, 0, len);
            }
            bos.flush();
            bos.close();
            bis.close();
        }
        zipFile.close();
        return compress;
    }

    /**
     * zip list of file
     *
     * @param resFileList file(dir) list
     * @param zipFile     output zip file
     * @param compressData compress data
     * @throws IOException io exception
     */
    public static void zipFiles(Collection<File> resFileList, File zipFile, HashMap<String, Integer> compressData) throws IOException {
        ZipOutputStream zipout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile), BUFFER));
        for (File resFile : resFileList) {
            if (resFile.exists()) {
                zipFile(resFile, zipout, "", compressData);
            }
        }
        zipout.close();
    }

    private static void zipFile(File resFile, ZipOutputStream zipout, String rootpath, HashMap<String, Integer> compressData) throws IOException {
        rootpath = rootpath + (rootpath.trim().length() == 0 ? "" : File.separator) + resFile.getName();
        if (resFile.isDirectory()) {
            File[] fileList = resFile.listFiles();
            for (File file : fileList) {
                zipFile(file, zipout, rootpath, compressData);
            }
        } else {
            final byte[] fileContents = readContents(resFile);
            //这里需要强转成linux格式，果然坑！！
            if (rootpath.contains("\\")) {
                rootpath = rootpath.replace("\\", "/");
            }
            if (!compressData.containsKey(rootpath)) {
                System.err.printf(String.format("do not have the compress data path =%s in resource.asrc\n", rootpath));
                //throw new IOException(String.format("do not have the compress data path=%s", rootpath));
                return;
            }
            int compressMethod = compressData.get(rootpath);
            ZipEntry entry = new ZipEntry(rootpath);

            if (compressMethod == ZipEntry.DEFLATED) {
                entry.setMethod(ZipEntry.DEFLATED);
            } else {
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(fileContents.length);
                final CRC32 checksumCalculator = new CRC32();
                checksumCalculator.update(fileContents);
                entry.setCrc(checksumCalculator.getValue());
            }
            zipout.putNextEntry(entry);
            zipout.write(fileContents);
            zipout.flush();
            zipout.closeEntry();
        }
    }

    private static byte[] readContents(final File file) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final int bufferSize = 4096;
        try {
            final FileInputStream in = new FileInputStream(file);
            final BufferedInputStream bIn = new BufferedInputStream(in);
            int length;
            byte[] buffer = new byte[bufferSize];
            byte[] bufferCopy;
            while ((length = bIn.read(buffer, 0, bufferSize)) != -1) {
                bufferCopy = new byte[length];
                System.arraycopy(buffer, 0, bufferCopy, 0, length);
                output.write(bufferCopy);
            }
            bIn.close();
        } finally {
            output.close();
        }
        return output.toByteArray();
    }
}
