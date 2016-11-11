/**
 *  Copyright 2014 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright 2016 sim sun <sunsj1231@gmail.com>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.tencent.mm.androlib.res.data;

import com.tencent.mm.androlib.AndrolibException;

import java.util.HashSet;


public final class ResType {
    private final String mName;

    private final ResPackage      mPackage;
    private final HashSet<String> specNames;

    public ResType(String name, ResPackage package_) {
        this.mName = name;
        this.mPackage = package_;
        specNames = new HashSet<>();
    }

    public String getName() {
        return mName;
    }

    public void putSpecProguardName(String name) throws AndrolibException {
        if (specNames.contains(name)) {
            throw new AndrolibException(String.format(
                "spec proguard name duplicate in a singal type %s, spec name: %s\n " +
                    "known issue: if you write a whilte list R.drawable.ab, and you have a png named ab.png, these may cost duplicate of ab\n", getName(), name
            ));
        }
        specNames.add(name);
    }

    @Override
    public String toString() {
        return mName;
    }
}
