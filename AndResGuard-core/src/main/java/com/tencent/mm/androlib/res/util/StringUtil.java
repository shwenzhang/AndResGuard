package com.tencent.mm.androlib.res.util;

public class StringUtil {
    public static boolean isPresent(final String string) {
        return string != null && string.length() > 0;
    }

    public static boolean isBlank(final String string) {
        return !isPresent(string);
    }
}
