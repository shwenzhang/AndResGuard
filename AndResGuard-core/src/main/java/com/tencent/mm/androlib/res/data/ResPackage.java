package com.tencent.mm.androlib.res.data;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * @author shwenzhang
 */
public class ResPackage {
    private final String mName;

    private final Map<Integer, String> mSpecNamesReplace;
    private final HashSet<String>      mSpecNamesBlock;
    private boolean mCanProguard = false;

    public ResPackage(int id, String name) {
        this.mName = name;
        mSpecNamesReplace = new LinkedHashMap<>();
        mSpecNamesBlock   = new HashSet<>();
    }

    public boolean isCanProguard() {
        return mCanProguard;
    }

    public void setCanProguard(boolean set) {
        mCanProguard = set;
    }

    public boolean hasSpecRepplace(String resID) {
        return mSpecNamesReplace.containsKey(resID);
    }

    public String getSpecRepplace(int resID) {
        return mSpecNamesReplace.get(resID);
    }


    public void putSpecNamesReplace(int resID, String value) {
        mSpecNamesReplace.put(resID, value);
    }

    public void putSpecNamesblock(String value) {
        mSpecNamesBlock.add(value);
    }


    public HashSet<String> getSpecNamesBlock() {
        return mSpecNamesBlock;
    }


    public String getName() {
        return mName;
    }

    @Override
    public String toString() {
        return mName;
    }
}
