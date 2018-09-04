/**
 * Copyright 2014 Ryszard Wiśniewski <brut.alll@gmail.com>
 * Copyright 2016 sim sun <sunsj1231@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.mm.androlib.res.data;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResPackage {
  private final String mName;

  private final Map<Integer, String> mSpecNamesReplace;
  private final HashSet<String> mSpecNamesBlock;
  private boolean mCanProguard = false;

  public ResPackage(int id, String name) {
    this.mName = name;
    mSpecNamesReplace = new LinkedHashMap<>();
    mSpecNamesBlock = new HashSet<>();
  }

  public boolean isCanResguard() {
    return mCanProguard;
  }

  public void setCanResguard(boolean set) {
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
