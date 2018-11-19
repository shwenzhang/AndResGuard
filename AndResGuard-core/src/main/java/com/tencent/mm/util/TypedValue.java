/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.mm.util;

import java.util.zip.ZipEntry;

/**
 * Container for a dynamically typed data value. Primarily used with
 */
public class TypedValue {
  public static final String UNZIP_FILE_PATH = "temp";

  public static final String COMMAND_7ZIP = "7za";
  public static final String COMMAND_ZIPALIGIN = "zipalign";

  public static final String OUT_7ZIP_FILE_PATH = "out_7zip";

  /**
   * 是7zip压缩使用，把制定为不压缩的拷到一起
   */
  public static final String STORED_FILE_PATH = "storedfiles";

  public static final String RES_FILE_PATH = "r";

  public static final String RES_MAPPING_FILE = "resource_mapping_";

  public static final String MERGE_DUPLICATED_RES_MAPPING_FILE = "merge_duplicated_res_mapping_";

  public static final int ZIP_STORED = ZipEntry.STORED;

  public static final int ZIP_DEFLATED = ZipEntry.DEFLATED;

  public static final int JDK_6 = 6;

  public static final String TXT_FILE = ".txt";

  public static final String XML_FILE = ".xml";

  public static final String CONFIG_FILE = "config.xml";

  /**
   * The value contains no data.
   */
  public static final int TYPE_NULL = 0x00;

  public static final int TYPE_STRING = 0x03;
};
