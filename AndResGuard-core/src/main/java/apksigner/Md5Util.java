package apksigner;


import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;


/**
 * MD5获取工具
 */
public class Md5Util {

    /**
     * 获取字符串的MD5
     *
     * @param str 要计算的字符串
     * @return 该文件的MD%
     */
    public static String getMD5Str(String str) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
            digest.update(str.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "";
        }
        return bytesToHexString(digest.digest());
    }

    /**
     * 获取单个文件的MD5值
     *
     * @param file 要计算的文件
     * @return 该文件的MD%
     */
    public static String getMD5Str(File file) {
        if (!file.isFile()) {
            return null;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
            digest.update(FileUtils.readFileToByteArray(file));
        } catch (Exception e) {
            return null;
        }
        return bytesToHexString(digest.digest());
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return "";
        }
        for (byte b : src) {
            int v = b & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

}