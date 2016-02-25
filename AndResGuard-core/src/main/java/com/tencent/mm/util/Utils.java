package com.tencent.mm.util;

import java.io.File;
import java.util.Iterator;

/**
 * Created by sun on 1/9/16.
 */
public class Utils {
    public static boolean isPresent(String str) {
        return str != null && str.length() > 0;
    }

    public static boolean isBlank(String str) {
        return !isPresent(str);
    }

    public static boolean isPresent(Iterator iterator) {
        return iterator != null && iterator.hasNext();
    }

    public static boolean isBlank(Iterator iterator) {
        return !isPresent(iterator);
    }

    public static String convetToPatternString(String input) {
        //将.换成\\.
        if (input.contains(".")) {
            input = input.replaceAll("\\.", "\\\\.");
        }
        //将？换成.,将*换成.*
        if (input.contains("?")) {
            input = input.replaceAll("\\?", "\\.");
        }
        if (input.contains("*")) {
            input = input.replace("*", ".+");
        }
        return input;
    }

    public static void cleanDir(File dir) {
        if (dir.exists()) {
            FileOperation.deleteDir(dir);
            dir.mkdirs();
        }
    }

    public static String spaceSafePath(String path) {
        return "\"" + path + "\"";
    }
}
