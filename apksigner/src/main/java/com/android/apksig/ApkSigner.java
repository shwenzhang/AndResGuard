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

package com.android.apksig;

import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.apk.MinSdkVersionException;
import com.android.apksig.internal.apk.v2.V2SchemeVerifier;
import com.android.apksig.internal.util.ByteBufferDataSource;
import com.android.apksig.internal.util.Pair;
import com.android.apksig.internal.zip.CentralDirectoryRecord;
import com.android.apksig.internal.zip.EocdRecord;
import com.android.apksig.internal.zip.LocalFileRecord;
import com.android.apksig.internal.zip.ZipUtils;
import com.android.apksig.util.DataSink;
import com.android.apksig.util.DataSinks;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.apksig.util.ReadableDataSink;
import com.android.apksig.zip.ZipFormatException;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * APK signer.
 *
 * <p>The signer preserves as much of the input APK as possible. For example, it preserves the
 * order of APK entries and preserves their contents, including compressed form and alignment of
 * data.
 *
 * <p>Use {@link Builder} to obtain instances of this signer.
 *
 * @see <a href="https://source.android.com/security/apksigning/index.html">Application Signing</a>
 */
public class ApkSigner {

    /**
     * Extensible data block/field header ID used for storing information about alignment of
     * uncompressed entries as well as for aligning the entries's data. See ZIP appnote.txt section
     * 4.5 Extensible data fields.
     */
    private static final short ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID = (short) 0xd935;

    /**
     * Minimum size (in bytes) of the extensible data block/field used for alignment of uncompressed
     * entries.
     */
    private static final short ALIGNMENT_ZIP_EXTRA_DATA_FIELD_MIN_SIZE_BYTES = 6;

    /**
     * Name of the Android manifest ZIP entry in APKs.
     */
    private static final String ANDROID_MANIFEST_ZIP_ENTRY_NAME = "AndroidManifest.xml";

    private final List<SignerConfig> mSignerConfigs;
    private final Integer mMinSdkVersion;
    private final boolean mV1SigningEnabled;
    private final boolean mV2SigningEnabled;
    private final boolean mOtherSignersSignaturesPreserved;
    private final String mCreatedBy;

    private final ApkSignerEngine mSignerEngine;

    private final File mInputApkFile;
    private final DataSource mInputApkDataSource;

    private final File mOutputApkFile;
    private final DataSink mOutputApkDataSink;
    private final DataSource mOutputApkDataSource;

    private ApkSigner(
            List<SignerConfig> signerConfigs,
            Integer minSdkVersion,
            boolean v1SigningEnabled,
            boolean v2SigningEnabled,
            boolean otherSignersSignaturesPreserved,
            String createdBy,
            ApkSignerEngine signerEngine,
            File inputApkFile,
            DataSource inputApkDataSource,
            File outputApkFile,
            DataSink outputApkDataSink,
            DataSource outputApkDataSource) {

        mSignerConfigs = signerConfigs;
        mMinSdkVersion = minSdkVersion;
        mV1SigningEnabled = v1SigningEnabled;
        mV2SigningEnabled = v2SigningEnabled;
        mOtherSignersSignaturesPreserved = otherSignersSignaturesPreserved;
        mCreatedBy = createdBy;

        mSignerEngine = signerEngine;

        mInputApkFile = inputApkFile;
        mInputApkDataSource = inputApkDataSource;

        mOutputApkFile = outputApkFile;
        mOutputApkDataSink = outputApkDataSink;
        mOutputApkDataSource = outputApkDataSource;
    }

    /**
     * Signs the input APK and outputs the resulting signed APK. The input APK is not modified.
     *
     * @throws IOException if an I/O error is encountered while reading or writing the APKs
     * @throws ApkFormatException if the input APK is malformed
     * @throws NoSuchAlgorithmException if the APK signatures cannot be produced or verified because
     *         a required cryptographic algorithm implementation is missing
     * @throws InvalidKeyException if a signature could not be generated because a signing key is
     *         not suitable for generating the signature
     * @throws SignatureException if an error occurred while generating or verifying a signature
     * @throws IllegalStateException if this signer's configuration is missing required information
     *         or if the signing engine is in an invalid state.
     */
    public void sign()
            throws IOException, ApkFormatException, NoSuchAlgorithmException, InvalidKeyException,
                    SignatureException, IllegalStateException {
        Closeable in = null;
        DataSource inputApk;
        try {
            if (mInputApkDataSource != null) {
                inputApk = mInputApkDataSource;
            } else if (mInputApkFile != null) {
                RandomAccessFile inputFile = new RandomAccessFile(mInputApkFile, "r");
                in = inputFile;
                inputApk = DataSources.asDataSource(inputFile);
            } else {
                throw new IllegalStateException("Input APK not specified");
            }

            Closeable out = null;
            try {
                DataSink outputApkOut;
                DataSource outputApkIn;
                if (mOutputApkDataSink != null) {
                    outputApkOut = mOutputApkDataSink;
                    outputApkIn = mOutputApkDataSource;
                } else if (mOutputApkFile != null) {
                    RandomAccessFile outputFile = new RandomAccessFile(mOutputApkFile, "rw");
                    out = outputFile;
                    outputFile.setLength(0);
                    outputApkOut = DataSinks.asDataSink(outputFile);
                    outputApkIn = DataSources.asDataSource(outputFile);
                } else {
                    throw new IllegalStateException("Output APK not specified");
                }

                sign(inputApk, outputApkOut, outputApkIn);
            } finally {
                if (out != null) {
                    out.close();
                }
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private void sign(
            DataSource inputApk,
            DataSink outputApkOut,
            DataSource outputApkIn)
                    throws IOException, ApkFormatException, NoSuchAlgorithmException,
                            InvalidKeyException, SignatureException {
        // Step 1. Find input APK's main ZIP sections
        ApkUtils.ZipSections inputZipSections;
        try {
            inputZipSections = ApkUtils.findZipSections(inputApk);
        } catch (ZipFormatException e) {
            throw new ApkFormatException("Malformed APK: not a ZIP archive", e);
        }
        long inputApkSigningBlockOffset = -1;
        DataSource inputApkSigningBlock = null;
        try {
            Pair<DataSource, Long> apkSigningBlockAndOffset =
                    V2SchemeVerifier.findApkSigningBlock(inputApk, inputZipSections);
            inputApkSigningBlock = apkSigningBlockAndOffset.getFirst();
            inputApkSigningBlockOffset = apkSigningBlockAndOffset.getSecond();
        } catch (V2SchemeVerifier.SignatureNotFoundException e) {
            // Input APK does not contain an APK Signing Block. That's OK. APKs are not required to
            // contain this block. It's only needed if the APK is signed using APK Signature Scheme
            // v2.
        }
        DataSource inputApkLfhSection =
                inputApk.slice(
                        0,
                        (inputApkSigningBlockOffset != -1)
                                ? inputApkSigningBlockOffset
                                : inputZipSections.getZipCentralDirectoryOffset());

        // Step 2. Parse the input APK's ZIP Central Directory
        ByteBuffer inputCd = getZipCentralDirectory(inputApk, inputZipSections);
        List<CentralDirectoryRecord> inputCdRecords =
                parseZipCentralDirectory(inputCd, inputZipSections);

        // Step 3. Obtain a signer engine instance
        ApkSignerEngine signerEngine;
        if (mSignerEngine != null) {
            // Use the provided signer engine
            signerEngine = mSignerEngine;
        } else {
            // Construct a signer engine from the provided parameters
            int minSdkVersion;
            if (mMinSdkVersion != null) {
                // No need to extract minSdkVersion from the APK's AndroidManifest.xml
                minSdkVersion = mMinSdkVersion;
            } else {
                // Need to extract minSdkVersion from the APK's AndroidManifest.xml
                minSdkVersion = getMinSdkVersionFromApk(inputCdRecords, inputApkLfhSection);
            }
            List<DefaultApkSignerEngine.SignerConfig> engineSignerConfigs =
                    new ArrayList<>(mSignerConfigs.size());
            for (SignerConfig signerConfig : mSignerConfigs) {
                engineSignerConfigs.add(
                        new DefaultApkSignerEngine.SignerConfig.Builder(
                                signerConfig.getName(),
                                signerConfig.getPrivateKey(),
                                signerConfig.getCertificates())
                                .build());
            }
            DefaultApkSignerEngine.Builder signerEngineBuilder =
                    new DefaultApkSignerEngine.Builder(engineSignerConfigs, minSdkVersion)
                            .setV1SigningEnabled(mV1SigningEnabled)
                            .setV2SigningEnabled(mV2SigningEnabled)
                            .setOtherSignersSignaturesPreserved(mOtherSignersSignaturesPreserved);
            if (mCreatedBy != null) {
                signerEngineBuilder.setCreatedBy(mCreatedBy);
            }
            signerEngine = signerEngineBuilder.build();
        }

        // Step 4. Provide the signer engine with the input APK's APK Signing Block (if any)
        if (inputApkSigningBlock != null) {
            signerEngine.inputApkSigningBlock(inputApkSigningBlock);
        }

        // Step 5. Iterate over input APK's entries and output the Local File Header + data of those
        // entries which need to be output. Entries are iterated in the order in which their Local
        // File Header records are stored in the file. This is to achieve better data locality in
        // case Central Directory entries are in the wrong order.
        List<CentralDirectoryRecord> inputCdRecordsSortedByLfhOffset =
                new ArrayList<>(inputCdRecords);
        Collections.sort(
                inputCdRecordsSortedByLfhOffset,
                CentralDirectoryRecord.BY_LOCAL_FILE_HEADER_OFFSET_COMPARATOR);
        int lastModifiedDateForNewEntries = -1;
        int lastModifiedTimeForNewEntries = -1;
        long inputOffset = 0;
        long outputOffset = 0;
        Map<String, CentralDirectoryRecord> outputCdRecordsByName =
                new HashMap<>(inputCdRecords.size());
        for (final CentralDirectoryRecord inputCdRecord : inputCdRecordsSortedByLfhOffset) {
            String entryName = inputCdRecord.getName();
            ApkSignerEngine.InputJarEntryInstructions entryInstructions =
                    signerEngine.inputJarEntry(entryName);
            boolean shouldOutput;
            switch (entryInstructions.getOutputPolicy()) {
                case OUTPUT:
                    shouldOutput = true;
                    break;
                case OUTPUT_BY_ENGINE:
                case SKIP:
                    shouldOutput = false;
                    break;
                default:
                    throw new RuntimeException(
                            "Unknown output policy: " + entryInstructions.getOutputPolicy());
            }

            long inputLocalFileHeaderStartOffset = inputCdRecord.getLocalFileHeaderOffset();
            if (inputLocalFileHeaderStartOffset > inputOffset) {
                // Unprocessed data in input starting at inputOffset and ending and the start of
                // this record's LFH. We output this data verbatim because this signer is supposed
                // to preserve as much of input as possible.
                long chunkSize = inputLocalFileHeaderStartOffset - inputOffset;
                inputApkLfhSection.feed(inputOffset, chunkSize, outputApkOut);
                outputOffset += chunkSize;
                inputOffset = inputLocalFileHeaderStartOffset;
            }
            LocalFileRecord inputLocalFileRecord;
            try {
                inputLocalFileRecord =
                        LocalFileRecord.getRecord(
                                inputApkLfhSection, inputCdRecord, inputApkLfhSection.size());
            } catch (ZipFormatException e) {
                throw new ApkFormatException("Malformed ZIP entry: " + inputCdRecord.getName(), e);
            }
            inputOffset += inputLocalFileRecord.getSize();

            ApkSignerEngine.InspectJarEntryRequest inspectEntryRequest =
                    entryInstructions.getInspectJarEntryRequest();
            if (inspectEntryRequest != null) {
                fulfillInspectInputJarEntryRequest(
                        inputApkLfhSection, inputLocalFileRecord, inspectEntryRequest);
            }

            if (shouldOutput) {
                // Find the max value of last modified, to be used for new entries added by the
                // signer.
                int lastModifiedDate = inputCdRecord.getLastModificationDate();
                int lastModifiedTime = inputCdRecord.getLastModificationTime();
                if ((lastModifiedDateForNewEntries == -1)
                        || (lastModifiedDate > lastModifiedDateForNewEntries)
                        || ((lastModifiedDate == lastModifiedDateForNewEntries)
                                && (lastModifiedTime > lastModifiedTimeForNewEntries))) {
                    lastModifiedDateForNewEntries = lastModifiedDate;
                    lastModifiedTimeForNewEntries = lastModifiedTime;
                }

                inspectEntryRequest = signerEngine.outputJarEntry(entryName);
                if (inspectEntryRequest != null) {
                    fulfillInspectInputJarEntryRequest(
                            inputApkLfhSection, inputLocalFileRecord, inspectEntryRequest);
                }

                // Output entry's Local File Header + data
                long outputLocalFileHeaderOffset = outputOffset;
                long outputLocalFileRecordSize =
                        outputInputJarEntryLfhRecordPreservingDataAlignment(
                                inputApkLfhSection,
                                inputLocalFileRecord,
                                outputApkOut,
                                outputLocalFileHeaderOffset);
                outputOffset += outputLocalFileRecordSize;

                // Enqueue entry's Central Directory record for output
                CentralDirectoryRecord outputCdRecord;
                if (outputLocalFileHeaderOffset == inputLocalFileRecord.getStartOffsetInArchive()) {
                    outputCdRecord = inputCdRecord;
                } else {
                    outputCdRecord =
                            inputCdRecord.createWithModifiedLocalFileHeaderOffset(
                                    outputLocalFileHeaderOffset);
                }
                outputCdRecordsByName.put(entryName, outputCdRecord);
            }
        }
        long inputLfhSectionSize = inputApkLfhSection.size();
        if (inputOffset < inputLfhSectionSize) {
            // Unprocessed data in input starting at inputOffset and ending and the end of the input
            // APK's LFH section. We output this data verbatim because this signer is supposed
            // to preserve as much of input as possible.
            long chunkSize = inputLfhSectionSize - inputOffset;
            inputApkLfhSection.feed(inputOffset, chunkSize, outputApkOut);
            outputOffset += chunkSize;
            inputOffset = inputLfhSectionSize;
        }

        // Step 6. Sort output APK's Central Directory records in the order in which they should
        // appear in the output
        List<CentralDirectoryRecord> outputCdRecords = new ArrayList<>(inputCdRecords.size() + 10);
        for (CentralDirectoryRecord inputCdRecord : inputCdRecords) {
            String entryName = inputCdRecord.getName();
            CentralDirectoryRecord outputCdRecord = outputCdRecordsByName.get(entryName);
            if (outputCdRecord != null) {
                outputCdRecords.add(outputCdRecord);
            }
        }

        // Step 7. Generate and output JAR signatures, if necessary. This may output more Local File
        // Header + data entries and add to the list of output Central Directory records.
        ApkSignerEngine.OutputJarSignatureRequest outputJarSignatureRequest =
                signerEngine.outputJarEntries();
        if (outputJarSignatureRequest != null) {
            if (lastModifiedDateForNewEntries == -1) {
                lastModifiedDateForNewEntries = 0x3a21; // Jan 1 2009 (DOS)
                lastModifiedTimeForNewEntries = 0;
            }
            for (ApkSignerEngine.OutputJarSignatureRequest.JarEntry entry :
                    outputJarSignatureRequest.getAdditionalJarEntries()) {
                String entryName = entry.getName();
                byte[] uncompressedData = entry.getData();
                ZipUtils.DeflateResult deflateResult =
                        ZipUtils.deflate(ByteBuffer.wrap(uncompressedData));
                byte[] compressedData = deflateResult.output;
                long uncompressedDataCrc32 = deflateResult.inputCrc32;

                ApkSignerEngine.InspectJarEntryRequest inspectEntryRequest =
                        signerEngine.outputJarEntry(entryName);
                if (inspectEntryRequest != null) {
                    inspectEntryRequest.getDataSink().consume(
                            uncompressedData, 0, uncompressedData.length);
                    inspectEntryRequest.done();
                }

                long localFileHeaderOffset = outputOffset;
                outputOffset +=
                        LocalFileRecord.outputRecordWithDeflateCompressedData(
                                entryName,
                                lastModifiedTimeForNewEntries,
                                lastModifiedDateForNewEntries,
                                compressedData,
                                uncompressedDataCrc32,
                                uncompressedData.length,
                                outputApkOut);


                outputCdRecords.add(
                        CentralDirectoryRecord.createWithDeflateCompressedData(
                                entryName,
                                lastModifiedTimeForNewEntries,
                                lastModifiedDateForNewEntries,
                                uncompressedDataCrc32,
                                compressedData.length,
                                uncompressedData.length,
                                localFileHeaderOffset));
            }
            outputJarSignatureRequest.done();
        }

        // Step 8. Construct output ZIP Central Directory in an in-memory buffer
        long outputCentralDirSizeBytes = 0;
        for (CentralDirectoryRecord record : outputCdRecords) {
            outputCentralDirSizeBytes += record.getSize();
        }
        if (outputCentralDirSizeBytes > Integer.MAX_VALUE) {
            throw new IOException(
                    "Output ZIP Central Directory too large: " + outputCentralDirSizeBytes
                            + " bytes");
        }
        ByteBuffer outputCentralDir = ByteBuffer.allocate((int) outputCentralDirSizeBytes);
        for (CentralDirectoryRecord record : outputCdRecords) {
            record.copyTo(outputCentralDir);
        }
        outputCentralDir.flip();
        DataSource outputCentralDirDataSource = new ByteBufferDataSource(outputCentralDir);
        long outputCentralDirStartOffset = outputOffset;
        int outputCentralDirRecordCount = outputCdRecords.size();

        // Step 9. Construct output ZIP End of Central Directory record in an in-memory buffer
        ByteBuffer outputEocd =
                EocdRecord.createWithModifiedCentralDirectoryInfo(
                        inputZipSections.getZipEndOfCentralDirectory(),
                        outputCentralDirRecordCount,
                        outputCentralDirDataSource.size(),
                        outputCentralDirStartOffset);

        // Step 10. Generate and output APK Signature Scheme v2 signatures, if necessary. This may
        // insert an APK Signing Block just before the output's ZIP Central Directory
        ApkSignerEngine.OutputApkSigningBlockRequest outputApkSigingBlockRequest =
                signerEngine.outputZipSections(
                        outputApkIn,
                        outputCentralDirDataSource,
                        DataSources.asDataSource(outputEocd));
        if (outputApkSigingBlockRequest != null) {
            byte[] outputApkSigningBlock = outputApkSigingBlockRequest.getApkSigningBlock();
            outputApkOut.consume(outputApkSigningBlock, 0, outputApkSigningBlock.length);
            ZipUtils.setZipEocdCentralDirectoryOffset(
                    outputEocd, outputCentralDirStartOffset + outputApkSigningBlock.length);
            outputApkSigingBlockRequest.done();
        }

        // Step 11. Output ZIP Central Directory and ZIP End of Central Directory
        outputCentralDirDataSource.feed(0, outputCentralDirDataSource.size(), outputApkOut);
        outputApkOut.consume(outputEocd);
        signerEngine.outputDone();
    }

    private static void fulfillInspectInputJarEntryRequest(
            DataSource lfhSection,
            LocalFileRecord localFileRecord,
            ApkSignerEngine.InspectJarEntryRequest inspectEntryRequest)
                    throws IOException, ApkFormatException {
        try {
            localFileRecord.outputUncompressedData(lfhSection, inspectEntryRequest.getDataSink());
        } catch (ZipFormatException e) {
            throw new ApkFormatException("Malformed ZIP entry: " + localFileRecord.getName(), e);
        }
        inspectEntryRequest.done();
    }

    private static long outputInputJarEntryLfhRecordPreservingDataAlignment(
            DataSource inputLfhSection,
            LocalFileRecord inputRecord,
            DataSink outputLfhSection,
            long outputOffset) throws IOException {
        long inputOffset = inputRecord.getStartOffsetInArchive();
        if (inputOffset == outputOffset) {
            // This record's data will be aligned same as in the input APK.
            return inputRecord.outputRecord(inputLfhSection, outputLfhSection);
        }
        int dataAlignmentMultiple = getInputJarEntryDataAlignmentMultiple(inputRecord);
        if ((dataAlignmentMultiple <= 1)
                || ((inputOffset % dataAlignmentMultiple)
                        == (outputOffset % dataAlignmentMultiple))) {
            // This record's data will be aligned same as in the input APK.
            return inputRecord.outputRecord(inputLfhSection, outputLfhSection);
        }

        long inputDataStartOffset = inputOffset + inputRecord.getDataStartOffsetInRecord();
        if ((inputDataStartOffset % dataAlignmentMultiple) != 0) {
            // This record's data is not aligned in the input APK. No need to align it in the
            // output.
            return inputRecord.outputRecord(inputLfhSection, outputLfhSection);
        }

        // This record's data needs to be re-aligned in the output. This is achieved using the
        // record's extra field.
        ByteBuffer aligningExtra =
                createExtraFieldToAlignData(
                        inputRecord.getExtra(),
                        outputOffset + inputRecord.getExtraFieldStartOffsetInsideRecord(),
                        dataAlignmentMultiple);
        return inputRecord.outputRecordWithModifiedExtra(
                inputLfhSection, aligningExtra, outputLfhSection);
    }

    private static int getInputJarEntryDataAlignmentMultiple(LocalFileRecord entry) {
        if (entry.isDataCompressed()) {
            // Compressed entries don't need to be aligned
            return 1;
        }

        // Attempt to obtain the alignment multiple from the entry's extra field.
        ByteBuffer extra = entry.getExtra();
        if (extra.hasRemaining()) {
            extra.order(ByteOrder.LITTLE_ENDIAN);
            // FORMAT: sequence of fields. Each field consists of:
            //   * uint16 ID
            //   * uint16 size
            //   * 'size' bytes: payload
            while (extra.remaining() >= 4) {
                short headerId  = extra.getShort();
                int dataSize = ZipUtils.getUnsignedInt16(extra);
                if (dataSize > extra.remaining()) {
                    // Malformed field -- insufficient input remaining
                    break;
                }
                if (headerId != ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID) {
                    // Skip this field
                    extra.position(extra.position() + dataSize);
                    continue;
                }
                // This is APK alignment field.
                // FORMAT:
                //  * uint16 alignment multiple (in bytes)
                //  * remaining bytes -- padding to achieve alignment of data which starts after
                //    the extra field
                if (dataSize < 2) {
                    // Malformed
                    break;
                }
                return ZipUtils.getUnsignedInt16(extra);
            }
        }

        // Fall back to filename-based defaults
        return (entry.getName().endsWith(".so")) ? 4096 : 4;
    }

    private static ByteBuffer createExtraFieldToAlignData(
            ByteBuffer original,
            long extraStartOffset,
            int dataAlignmentMultiple) {
        if (dataAlignmentMultiple <= 1) {
            return original;
        }

        // In the worst case scenario, we'll increase the output size by 6 + dataAlignment - 1.
        ByteBuffer result = ByteBuffer.allocate(original.remaining() + 5 + dataAlignmentMultiple);
        result.order(ByteOrder.LITTLE_ENDIAN);

        // Step 1. Output all extra fields other than the one which is to do with alignment
        // FORMAT: sequence of fields. Each field consists of:
        //   * uint16 ID
        //   * uint16 size
        //   * 'size' bytes: payload
        while (original.remaining() >= 4) {
            short headerId  = original.getShort();
            int dataSize = ZipUtils.getUnsignedInt16(original);
            if (dataSize > original.remaining()) {
                // Malformed field -- insufficient input remaining
                break;
            }
            if (((headerId == 0) && (dataSize == 0))
                    || (headerId == ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID)) {
                // Ignore the field if it has to do with the old APK data alignment method (filling
                // the extra field with 0x00 bytes) or the new APK data alignment method.
                original.position(original.position() + dataSize);
                continue;
            }
            // Copy this field (including header) to the output
            original.position(original.position() - 4);
            int originalLimit = original.limit();
            original.limit(original.position() + 4 + dataSize);
            result.put(original);
            original.limit(originalLimit);
        }

        // Step 2. Add alignment field
        // FORMAT:
        //  * uint16 extra header ID
        //  * uint16 extra data size
        //        Payload ('data size' bytes)
        //      * uint16 alignment multiple (in bytes)
        //      * remaining bytes -- padding to achieve alignment of data which starts after the
        //        extra field
        long dataMinStartOffset =
                extraStartOffset + result.position()
                        + ALIGNMENT_ZIP_EXTRA_DATA_FIELD_MIN_SIZE_BYTES;
        int paddingSizeBytes =
                (dataAlignmentMultiple - ((int) (dataMinStartOffset % dataAlignmentMultiple)))
                        % dataAlignmentMultiple;
        result.putShort(ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID);
        ZipUtils.putUnsignedInt16(result, 2 + paddingSizeBytes);
        ZipUtils.putUnsignedInt16(result, dataAlignmentMultiple);
        result.position(result.position() + paddingSizeBytes);
        result.flip();

        return result;
    }

    private static ByteBuffer getZipCentralDirectory(
            DataSource apk,
            ApkUtils.ZipSections apkSections) throws IOException, ApkFormatException {
        long cdSizeBytes = apkSections.getZipCentralDirectorySizeBytes();
        if (cdSizeBytes > Integer.MAX_VALUE) {
            throw new ApkFormatException("ZIP Central Directory too large: " + cdSizeBytes);
        }
        long cdOffset = apkSections.getZipCentralDirectoryOffset();
        ByteBuffer cd = apk.getByteBuffer(cdOffset, (int) cdSizeBytes);
        cd.order(ByteOrder.LITTLE_ENDIAN);
        return cd;
    }

    private static List<CentralDirectoryRecord> parseZipCentralDirectory(
            ByteBuffer cd,
            ApkUtils.ZipSections apkSections) throws ApkFormatException {
        long cdOffset = apkSections.getZipCentralDirectoryOffset();
        int expectedCdRecordCount = apkSections.getZipCentralDirectoryRecordCount();
        List<CentralDirectoryRecord> cdRecords = new ArrayList<>(expectedCdRecordCount);
        Set<String> entryNames = new HashSet<>(expectedCdRecordCount);
        for (int i = 0; i < expectedCdRecordCount; i++) {
            CentralDirectoryRecord cdRecord;
            int offsetInsideCd = cd.position();
            try {
                cdRecord = CentralDirectoryRecord.getRecord(cd);
            } catch (ZipFormatException e) {
                throw new ApkFormatException(
                        "Malformed ZIP Central Directory record #" + (i + 1)
                                + " at file offset " + (cdOffset + offsetInsideCd),
                        e);
            }
            String entryName = cdRecord.getName();
            if (!entryNames.add(entryName)) {
                throw new ApkFormatException(
                        "Multiple ZIP entries with the same name: " + entryName);
            }
            cdRecords.add(cdRecord);
        }
        if (cd.hasRemaining()) {
            throw new ApkFormatException(
                    "Unused space at the end of ZIP Central Directory: " + cd.remaining()
                            + " bytes starting at file offset " + (cdOffset + cd.position()));
        }

        return cdRecords;
    }

    /**
     * Returns the minimum Android version (API Level) supported by the provided APK. This is based
     * on the {@code android:minSdkVersion} attributes of the APK's {@code AndroidManifest.xml}.
     */
    static int getMinSdkVersionFromApk(
            List<CentralDirectoryRecord> cdRecords, DataSource lhfSection)
                    throws IOException, MinSdkVersionException {
        CentralDirectoryRecord androidManifestCdRecord = null;
        for (CentralDirectoryRecord cdRecord : cdRecords) {
            if (ANDROID_MANIFEST_ZIP_ENTRY_NAME.equals(cdRecord.getName())) {
                androidManifestCdRecord = cdRecord;
                break;
            }
        }
        if (androidManifestCdRecord == null) {
            throw new MinSdkVersionException(
                    "Unable to determine APK's minimum supported Android platform version"
                            + ": APK is missing " + ANDROID_MANIFEST_ZIP_ENTRY_NAME);
        }
        byte[] androidManifest;
        try {
            androidManifest =
                    LocalFileRecord.getUncompressedData(
                            lhfSection, androidManifestCdRecord, lhfSection.size());
        } catch (ZipFormatException e) {
            throw new MinSdkVersionException(
                    "Unable to determine APK's minimum supported Android platform version"
                            + ": malformed ZIP entry: " + androidManifestCdRecord.getName(),
                    e);
        }
        return ApkUtils.getMinSdkVersionFromBinaryAndroidManifest(ByteBuffer.wrap(androidManifest));
    }

    /**
     * Configuration of a signer.
     *
     * <p>Use {@link Builder} to obtain configuration instances.
     */
    public static class SignerConfig {
        private final String mName;
        private final PrivateKey mPrivateKey;
        private final List<X509Certificate> mCertificates;

        private SignerConfig(
                String name,
                PrivateKey privateKey,
                List<X509Certificate> certificates) {
            mName = name;
            mPrivateKey = privateKey;
            mCertificates = Collections.unmodifiableList(new ArrayList<>(certificates));
        }

        /**
         * Returns the name of this signer.
         */
        public String getName() {
            return mName;
        }

        /**
         * Returns the signing key of this signer.
         */
        public PrivateKey getPrivateKey() {
            return mPrivateKey;
        }

        /**
         * Returns the certificate(s) of this signer. The first certificate's public key corresponds
         * to this signer's private key.
         */
        public List<X509Certificate> getCertificates() {
            return mCertificates;
        }

        /**
         * Builder of {@link SignerConfig} instances.
         */
        public static class Builder {
            private final String mName;
            private final PrivateKey mPrivateKey;
            private final List<X509Certificate> mCertificates;

            /**
             * Constructs a new {@code Builder}.
             *
             * @param name signer's name. The name is reflected in the name of files comprising the
             *        JAR signature of the APK.
             * @param privateKey signing key
             * @param certificates list of one or more X.509 certificates. The subject public key of
             *        the first certificate must correspond to the {@code privateKey}.
             */
            public Builder(
                    String name,
                    PrivateKey privateKey,
                    List<X509Certificate> certificates) {
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Empty name");
                }
                mName = name;
                mPrivateKey = privateKey;
                mCertificates = new ArrayList<>(certificates);
            }

            /**
             * Returns a new {@code SignerConfig} instance configured based on the configuration of
             * this builder.
             */
            public SignerConfig build() {
                return new SignerConfig(
                        mName,
                        mPrivateKey,
                        mCertificates);
            }
        }
    }

    /**
     * Builder of {@link ApkSigner} instances.
     *
     * <p>The builder requires the following information to construct a working {@code ApkSigner}:
     * <ul>
     * <li>Signer configs or {@link ApkSignerEngine} -- provided in the constructor,</li>
     * <li>APK to be signed -- see {@link #setInputApk(File) setInputApk} variants,</li>
     * <li>where to store the signed APK -- see {@link #setOutputApk(File) setOutputApk} variants.
     * </li>
     * </ul>
     */
    public static class Builder {
        private final List<SignerConfig> mSignerConfigs;
        private boolean mV1SigningEnabled = true;
        private boolean mV2SigningEnabled = true;
        private boolean mOtherSignersSignaturesPreserved;
        private String mCreatedBy;
        private Integer mMinSdkVersion;

        private final ApkSignerEngine mSignerEngine;

        private File mInputApkFile;
        private DataSource mInputApkDataSource;

        private File mOutputApkFile;
        private DataSink mOutputApkDataSink;
        private DataSource mOutputApkDataSource;

        /**
         * Constructs a new {@code Builder} for an {@code ApkSigner} which signs using the provided
         * signer configurations. The resulting signer may be further customized through this
         * builder's setters, such as {@link #setMinSdkVersion(int)},
         * {@link #setV1SigningEnabled(boolean)}, {@link #setV2SigningEnabled(boolean)},
         * {@link #setOtherSignersSignaturesPreserved(boolean)}, {@link #setCreatedBy(String)}.
         *
         * <p>{@link #Builder(ApkSignerEngine)} is an alternative for advanced use cases where
         * more control over low-level details of signing is desired.
         */
        public Builder(List<SignerConfig> signerConfigs) {
            if (signerConfigs.isEmpty()) {
                throw new IllegalArgumentException("At least one signer config must be provided");
            }
            mSignerConfigs = new ArrayList<>(signerConfigs);
            mSignerEngine = null;
        }

        /**
         * Constructs a new {@code Builder} for an {@code ApkSigner} which signs using the
         * provided signing engine. This is meant for advanced use cases where more control is
         * needed over the lower-level details of signing. For typical use cases,
         * {@link #Builder(List)} is more appropriate.
         */
        public Builder(ApkSignerEngine signerEngine) {
            if (signerEngine == null) {
                throw new NullPointerException("signerEngine == null");
            }
            mSignerEngine = signerEngine;
            mSignerConfigs = null;
        }

        /**
         * Sets the APK to be signed.
         *
         * @see #setInputApk(DataSource)
         */
        public Builder setInputApk(File inputApk) {
            if (inputApk == null) {
                throw new NullPointerException("inputApk == null");
            }
            mInputApkFile = inputApk;
            mInputApkDataSource = null;
            return this;
        }

        /**
         * Sets the APK to be signed.
         *
         * @see #setInputApk(File)
         */
        public Builder setInputApk(DataSource inputApk) {
            if (inputApk == null) {
                throw new NullPointerException("inputApk == null");
            }
            mInputApkDataSource = inputApk;
            mInputApkFile = null;
            return this;
        }

        /**
         * Sets the location of the output (signed) APK. {@code ApkSigner} will create this file if
         * it doesn't exist.
         *
         * @see #setOutputApk(ReadableDataSink)
         * @see #setOutputApk(DataSink, DataSource)
         */
        public Builder setOutputApk(File outputApk) {
            if (outputApk == null) {
                throw new NullPointerException("outputApk == null");
            }
            mOutputApkFile = outputApk;
            mOutputApkDataSink = null;
            mOutputApkDataSource = null;
            return this;
        }

        /**
         * Sets the readable data sink which will receive the output (signed) APK. After signing,
         * the contents of the output APK will be available via the {@link DataSource} interface of
         * the sink.
         *
         * <p>This variant of {@code setOutputApk} is useful for avoiding writing the output APK to
         * a file. For example, an in-memory data sink, such as
         * {@link DataSinks#newInMemoryDataSink()}, could be used instead of a file.
         *
         * @see #setOutputApk(File)
         * @see #setOutputApk(DataSink, DataSource)
         */
        public Builder setOutputApk(ReadableDataSink outputApk) {
            if (outputApk == null) {
                throw new NullPointerException("outputApk == null");
            }
            return setOutputApk(outputApk, outputApk);
        }

        /**
         * Sets the sink which will receive the output (signed) APK. Data received by the
         * {@code outputApkOut} sink must be visible through the {@code outputApkIn} data source.
         *
         * <p>This is an advanced variant of {@link #setOutputApk(ReadableDataSink)}, enabling the
         * sink and the source to be different objects.
         *
         * @see #setOutputApk(ReadableDataSink)
         * @see #setOutputApk(File)
         */
        public Builder setOutputApk(DataSink outputApkOut, DataSource outputApkIn) {
            if (outputApkOut == null) {
                throw new NullPointerException("outputApkOut == null");
            }
            if (outputApkIn == null) {
                throw new NullPointerException("outputApkIn == null");
            }
            mOutputApkFile = null;
            mOutputApkDataSink = outputApkOut;
            mOutputApkDataSource = outputApkIn;
            return this;
        }

        /**
         * Sets the minimum Android platform version (API Level) on which APK signatures produced
         * by the signer being built must verify. This method is useful for overriding the default
         * behavior where the minimum API Level is obtained from the {@code android:minSdkVersion}
         * attribute of the APK's {@code AndroidManifest.xml}.
         *
         * <p><em>Note:</em> This method may result in APK signatures which don't verify on some
         * Android platform versions supported by the APK.
         *
         * <p><em>Note:</em> This method may only be invoked when this builder is not initialized
         * with an {@link ApkSignerEngine}.
         *
         * @throws IllegalStateException if this builder was initialized with an
         *         {@link ApkSignerEngine}
         */
        public Builder setMinSdkVersion(int minSdkVersion) {
            checkInitializedWithoutEngine();
            mMinSdkVersion = minSdkVersion;
            return this;
        }

        /**
         * Sets whether the APK should be signed using JAR signing (aka v1 signature scheme).
         *
         * <p>By default, whether APK is signed using JAR signing is determined by
         * {@code ApkSigner}, based on the platform versions supported by the APK or specified using
         * {@link #setMinSdkVersion(int)}. Disabling JAR signing will result in APK signatures which
         * don't verify on Android Marshmallow (Android 6.0, API Level 23) and lower.
         *
         * <p><em>Note:</em> This method may only be invoked when this builder is not initialized
         * with an {@link ApkSignerEngine}.
         *
         * @param enabled {@code true} to require the APK to be signed using JAR signing,
         *        {@code false} to require the APK to not be signed using JAR signing.
         *
         * @throws IllegalStateException if this builder was initialized with an
         *         {@link ApkSignerEngine}
         *
         * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File">JAR signing</a>
         */
        public Builder setV1SigningEnabled(boolean enabled) {
            checkInitializedWithoutEngine();
            mV1SigningEnabled = enabled;
            return this;
        }

        /**
         * Sets whether the APK should be signed using APK Signature Scheme v2 (aka v2 signature
         * scheme).
         *
         * <p>By default, whether APK is signed using APK Signature Scheme v2 is determined by
         * {@code ApkSigner} based on the platform versions supported by the APK or specified using
         * {@link #setMinSdkVersion(int)}.
         *
         * <p><em>Note:</em> This method may only be invoked when this builder is not initialized
         * with an {@link ApkSignerEngine}.
         *
         * @param enabled {@code true} to require the APK to be signed using APK Signature Scheme
         *        v2, {@code false} to require the APK to not be signed using APK Signature Scheme
         *        v2.
         *
         * @throws IllegalStateException if this builder was initialized with an
         *         {@link ApkSignerEngine}
         *
         * @see <a href="https://source.android.com/security/apksigning/v2.html">APK Signature Scheme v2</a>
         */
        public Builder setV2SigningEnabled(boolean enabled) {
            checkInitializedWithoutEngine();
            mV2SigningEnabled = enabled;
            return this;
        }

        /**
         * Sets whether signatures produced by signers other than the ones configured in this engine
         * should be copied from the input APK to the output APK.
         *
         * <p>By default, signatures of other signers are omitted from the output APK.
         *
         * <p><em>Note:</em> This method may only be invoked when this builder is not initialized
         * with an {@link ApkSignerEngine}.
         *
         * @throws IllegalStateException if this builder was initialized with an
         *         {@link ApkSignerEngine}
         */
        public Builder setOtherSignersSignaturesPreserved(boolean preserved) {
            checkInitializedWithoutEngine();
            mOtherSignersSignaturesPreserved = preserved;
            return this;
        }

        /**
         * Sets the value of the {@code Created-By} field in JAR signature files.
         *
         * <p><em>Note:</em> This method may only be invoked when this builder is not initialized
         * with an {@link ApkSignerEngine}.
         *
         * @throws IllegalStateException if this builder was initialized with an
         *         {@link ApkSignerEngine}
         */
        public Builder setCreatedBy(String createdBy) {
            checkInitializedWithoutEngine();
            if (createdBy == null) {
                throw new NullPointerException();
            }
            mCreatedBy = createdBy;
            return this;
        }

        private void checkInitializedWithoutEngine() {
            if (mSignerEngine != null) {
                throw new IllegalStateException(
                        "Operation is not available when builder initialized with an engine");
            }
        }

        /**
         * Returns a new {@code ApkSigner} instance initialized according to the configuration of
         * this builder.
         */
        public ApkSigner build() {
            return new ApkSigner(
                    mSignerConfigs,
                    mMinSdkVersion,
                    mV1SigningEnabled,
                    mV2SigningEnabled,
                    mOtherSignersSignaturesPreserved,
                    mCreatedBy,
                    mSignerEngine,
                    mInputApkFile,
                    mInputApkDataSource,
                    mOutputApkFile,
                    mOutputApkDataSink,
                    mOutputApkDataSource);
        }
    }
}
