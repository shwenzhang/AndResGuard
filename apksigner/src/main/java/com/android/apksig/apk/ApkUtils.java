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

package com.android.apksig.apk;

import com.android.apksig.internal.util.Pair;
import com.android.apksig.internal.zip.ZipUtils;
import com.android.apksig.util.DataSource;
import com.android.apksig.zip.ZipFormatException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Comparator;

/**
 * APK utilities.
 */
public abstract class ApkUtils {

    private ApkUtils() {}

    /**
     * Finds the main ZIP sections of the provided APK.
     *
     * @throws IOException if an I/O error occurred while reading the APK
     * @throws ZipFormatException if the APK is malformed
     */
    public static ZipSections findZipSections(DataSource apk)
            throws IOException, ZipFormatException {
        Pair<ByteBuffer, Long> eocdAndOffsetInFile =
                ZipUtils.findZipEndOfCentralDirectoryRecord(apk);
        if (eocdAndOffsetInFile == null) {
            throw new ZipFormatException("ZIP End of Central Directory record not found");
        }

        ByteBuffer eocdBuf = eocdAndOffsetInFile.getFirst();
        long eocdOffset = eocdAndOffsetInFile.getSecond();
        eocdBuf.order(ByteOrder.LITTLE_ENDIAN);
        long cdStartOffset = ZipUtils.getZipEocdCentralDirectoryOffset(eocdBuf);
        if (cdStartOffset > eocdOffset) {
            throw new ZipFormatException(
                    "ZIP Central Directory start offset out of range: " + cdStartOffset
                        + ". ZIP End of Central Directory offset: " + eocdOffset);
        }

        long cdSizeBytes = ZipUtils.getZipEocdCentralDirectorySizeBytes(eocdBuf);
        long cdEndOffset = cdStartOffset + cdSizeBytes;
        if (cdEndOffset > eocdOffset) {
            throw new ZipFormatException(
                    "ZIP Central Directory overlaps with End of Central Directory"
                            + ". CD end: " + cdEndOffset
                            + ", EoCD start: " + eocdOffset);
        }

        int cdRecordCount = ZipUtils.getZipEocdCentralDirectoryTotalRecordCount(eocdBuf);

        return new ZipSections(
                cdStartOffset,
                cdSizeBytes,
                cdRecordCount,
                eocdOffset,
                eocdBuf);
    }

    /**
     * Information about the ZIP sections of an APK.
     */
    public static class ZipSections {
        private final long mCentralDirectoryOffset;
        private final long mCentralDirectorySizeBytes;
        private final int mCentralDirectoryRecordCount;
        private final long mEocdOffset;
        private final ByteBuffer mEocd;

        public ZipSections(
                long centralDirectoryOffset,
                long centralDirectorySizeBytes,
                int centralDirectoryRecordCount,
                long eocdOffset,
                ByteBuffer eocd) {
            mCentralDirectoryOffset = centralDirectoryOffset;
            mCentralDirectorySizeBytes = centralDirectorySizeBytes;
            mCentralDirectoryRecordCount = centralDirectoryRecordCount;
            mEocdOffset = eocdOffset;
            mEocd = eocd;
        }

        /**
         * Returns the start offset of the ZIP Central Directory. This value is taken from the
         * ZIP End of Central Directory record.
         */
        public long getZipCentralDirectoryOffset() {
            return mCentralDirectoryOffset;
        }

        /**
         * Returns the size (in bytes) of the ZIP Central Directory. This value is taken from the
         * ZIP End of Central Directory record.
         */
        public long getZipCentralDirectorySizeBytes() {
            return mCentralDirectorySizeBytes;
        }

        /**
         * Returns the number of records in the ZIP Central Directory. This value is taken from the
         * ZIP End of Central Directory record.
         */
        public int getZipCentralDirectoryRecordCount() {
            return mCentralDirectoryRecordCount;
        }

        /**
         * Returns the start offset of the ZIP End of Central Directory record. The record extends
         * until the very end of the APK.
         */
        public long getZipEndOfCentralDirectoryOffset() {
            return mEocdOffset;
        }

        /**
         * Returns the contents of the ZIP End of Central Directory.
         */
        public ByteBuffer getZipEndOfCentralDirectory() {
            return mEocd;
        }
    }

    /**
     * Sets the offset of the start of the ZIP Central Directory in the APK's ZIP End of Central
     * Directory record.
     *
     * @param zipEndOfCentralDirectory APK's ZIP End of Central Directory record
     * @param offset offset of the ZIP Central Directory relative to the start of the archive. Must
     *        be between {@code 0} and {@code 2^32 - 1} inclusive.
     */
    public static void setZipEocdCentralDirectoryOffset(
            ByteBuffer zipEndOfCentralDirectory, long offset) {
        ByteBuffer eocd = zipEndOfCentralDirectory.slice();
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        ZipUtils.setZipEocdCentralDirectoryOffset(eocd, offset);
    }

    /**
     * Name of the Android manifest ZIP entry in APKs.
     */
    private static final String ANDROID_MANIFEST_ZIP_ENTRY_NAME = "AndroidManifest.xml";

    /**
     * Android resource ID of the {@code android:minSdkVersion} attribute in AndroidManifest.xml.
     */
    private static final int MIN_SDK_VERSION_ATTR_ID = 0x0101020c;

    /**
     * Returns the lowest Android platform version (API Level) supported by an APK with the
     * provided {@code AndroidManifest.xml}.
     *
     * @param androidManifestContents contents of {@code AndroidManifest.xml} in binary Android
     *        resource format
     *
     * @throws MinSdkVersionException if an error occurred while determining the API Level
     */
    public static int getMinSdkVersionFromBinaryAndroidManifest(
            ByteBuffer androidManifestContents) throws MinSdkVersionException {
        // IMPLEMENTATION NOTE: Minimum supported Android platform version number is declared using
        // uses-sdk elements which are children of the top-level manifest element. uses-sdk element
        // declares the minimum supported platform version using the android:minSdkVersion attribute
        // whose default value is 1.
        // For each encountered uses-sdk element, the Android runtime checks that its minSdkVersion
        // is not higher than the runtime's API Level and rejects APKs if it is higher. Thus, the
        // effective minSdkVersion value is the maximum over the encountered minSdkVersion values.

        try {
            // If no uses-sdk elements are encountered, Android accepts the APK. We treat this
            // scenario as though the minimum supported API Level is 1.
            int result = 1;

            AndroidBinXmlParser parser = new AndroidBinXmlParser(androidManifestContents);
            int eventType = parser.getEventType();
            while (eventType != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
                if ((eventType == AndroidBinXmlParser.EVENT_START_ELEMENT)
                        && (parser.getDepth() == 2)
                        && ("uses-sdk".equals(parser.getName()))
                        && (parser.getNamespace().isEmpty())) {
                    // In each uses-sdk element, minSdkVersion defaults to 1
                    int minSdkVersion = 1;
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (parser.getAttributeNameResourceId(i) == MIN_SDK_VERSION_ATTR_ID) {
                            int valueType = parser.getAttributeValueType(i);
                            switch (valueType) {
                                case AndroidBinXmlParser.VALUE_TYPE_INT:
                                    minSdkVersion = parser.getAttributeIntValue(i);
                                    break;
                                case AndroidBinXmlParser.VALUE_TYPE_STRING:
                                    minSdkVersion =
                                            getMinSdkVersionForCodename(
                                                    parser.getAttributeStringValue(i));
                                    break;
                                default:
                                    throw new MinSdkVersionException(
                                            "Unable to determine APK's minimum supported Android"
                                                    + ": unsupported value type in "
                                                    + ANDROID_MANIFEST_ZIP_ENTRY_NAME + "'s"
                                                    + " minSdkVersion"
                                                    + ". Only integer values supported.");
                            }
                            break;
                        }
                    }
                    result = Math.max(result, minSdkVersion);
                }
                eventType = parser.next();
            }

            return result;
        } catch (AndroidBinXmlParser.XmlParserException e) {
            throw new MinSdkVersionException(
                    "Unable to determine APK's minimum supported Android platform version"
                            + ": malformed binary resource: " + ANDROID_MANIFEST_ZIP_ENTRY_NAME,
                    e);
        }
    }

    private static class CodenamesLazyInitializer {

        /**
         * List of platform codename (first letter of) to API Level mappings. The list must be
         * sorted by the first letter. For codenames not in the list, the assumption is that the API
         * Level is incremented by one for every increase in the codename's first letter.
         */
        @SuppressWarnings("unchecked")
        private static final Pair<Character, Integer>[] SORTED_CODENAMES_FIRST_CHAR_TO_API_LEVEL =
                new Pair[] {
            Pair.of('C', 2),
            Pair.of('D', 3),
            Pair.of('E', 4),
            Pair.of('F', 7),
            Pair.of('G', 8),
            Pair.of('H', 10),
            Pair.of('I', 13),
            Pair.of('J', 15),
            Pair.of('K', 18),
            Pair.of('L', 20),
            Pair.of('M', 22),
            Pair.of('N', 23),
            Pair.of('O', 25),
        };

        private static final Comparator<Pair<Character, Integer>> CODENAME_FIRST_CHAR_COMPARATOR =
                new ByFirstComparator();

        private static class ByFirstComparator implements Comparator<Pair<Character, Integer>> {
            @Override
            public int compare(Pair<Character, Integer> o1, Pair<Character, Integer> o2) {
                char c1 = o1.getFirst();
                char c2 = o2.getFirst();
                return c1 - c2;
            }
        }
    }

    /**
     * Returns the API Level corresponding to the provided platform codename.
     *
     * <p>This method is pessimistic. It returns a value one lower than the API Level with which the
     * platform is actually released (e.g., 23 for N which was released as API Level 24). This is
     * because new features which first appear in an API Level are not available in the early days
     * of that platform version's existence, when the platform only has a codename. Moreover, this
     * method currently doesn't differentiate between initial and MR releases, meaning API Level
     * returned for MR releases may be more than one lower than the API Level with which the
     * platform version is actually released.
     *
     * @throws CodenameMinSdkVersionException if the {@code codename} is not supported
     */
    static int getMinSdkVersionForCodename(String codename) throws CodenameMinSdkVersionException {
        char firstChar = codename.isEmpty() ? ' ' : codename.charAt(0);
        // Codenames are case-sensitive. Only codenames starting with A-Z are supported for now.
        // We only look at the first letter of the codename as this is the most important letter.
        if ((firstChar >= 'A') && (firstChar <= 'Z')) {
            Pair<Character, Integer>[] sortedCodenamesFirstCharToApiLevel =
                    CodenamesLazyInitializer.SORTED_CODENAMES_FIRST_CHAR_TO_API_LEVEL;
            int searchResult =
                    Arrays.binarySearch(
                            sortedCodenamesFirstCharToApiLevel,
                            Pair.of(firstChar, null), // second element of the pair is ignored here
                            CodenamesLazyInitializer.CODENAME_FIRST_CHAR_COMPARATOR);
            if (searchResult >= 0) {
                // Exact match -- searchResult is the index of the matching element
                return sortedCodenamesFirstCharToApiLevel[searchResult].getSecond();
            }
            // Not an exact match -- searchResult is negative and is -(insertion index) - 1.
            // The element at insertionIndex - 1 (if present) is smaller than firstChar and the
            // element at insertionIndex (if present) is greater than firstChar.
            int insertionIndex = -1 - searchResult; // insertionIndex is in [0; array length]
            if (insertionIndex == 0) {
                // 'A' or 'B' -- never released to public
                return 1;
            } else {
                // The element at insertionIndex - 1 is the newest older codename.
                // API Level bumped by at least 1 for every change in the first letter of codename
                Pair<Character, Integer> newestOlderCodenameMapping =
                        sortedCodenamesFirstCharToApiLevel[insertionIndex - 1];
                char newestOlderCodenameFirstChar = newestOlderCodenameMapping.getFirst();
                int newestOlderCodenameApiLevel = newestOlderCodenameMapping.getSecond();
                return newestOlderCodenameApiLevel + (firstChar - newestOlderCodenameFirstChar);
            }
        }

        throw new CodenameMinSdkVersionException(
                "Unable to determine APK's minimum supported Android platform version"
                        + " : Unsupported codename in " + ANDROID_MANIFEST_ZIP_ENTRY_NAME
                        + "'s minSdkVersion: \"" + codename + "\"",
                codename);
    }
}
