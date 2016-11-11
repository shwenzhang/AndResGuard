/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.mm.androlib.res.util;

import com.tencent.mm.directory.Directory;
import com.tencent.mm.directory.DirectoryException;
import com.tencent.mm.directory.FileDirectory;
import com.tencent.mm.directory.ZipRODirectory;

import java.io.File;
import java.net.URI;


public class ExtFile extends File {
    private Directory mDirectory;

    public ExtFile(File file) {
        super(file.getPath());
    }

    public ExtFile(URI uri) {
        super(uri);
    }

    public ExtFile(File parent, String child) {
        super(parent, child);
    }

    public ExtFile(String parent, String child) {
        super(parent, child);
    }

    public ExtFile(String pathname) {
        super(pathname);
    }

    public Directory getDirectory() throws DirectoryException {
        if (mDirectory == null) {
            if (isDirectory()) {
                mDirectory = new FileDirectory(this);
            } else {
                mDirectory = new ZipRODirectory(this);
            }
        }
        return mDirectory;
    }
}
