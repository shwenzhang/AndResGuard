/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.apksig.internal.util;

/**
 * Android SDK version / API Level constants.
 */
public abstract class AndroidSdkVersion {

    /** Hidden constructor to prevent instantiation. */
    private AndroidSdkVersion() {}

    /** Android 2.3. */
    public static final int GINGERBREAD = 9;

    /** Android 4.3. The revenge of the beans. */
    public static final int JELLY_BEAN_MR2 = 18;

    /** Android 5.0. A flat one with beautiful shadows. But still tasty. */
    public static final int LOLLIPOP = 21;

    /** Android 7.0. N is for Nougat. */
    public static final int N = 24;
}
