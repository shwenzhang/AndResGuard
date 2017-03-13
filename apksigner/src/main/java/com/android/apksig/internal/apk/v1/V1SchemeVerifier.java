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

package com.android.apksig.internal.apk.v1;

import com.android.apksig.ApkVerifier.Issue;
import com.android.apksig.ApkVerifier.IssueWithParams;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.internal.jar.ManifestParser;
import com.android.apksig.internal.util.AndroidSdkVersion;
import com.android.apksig.internal.util.InclusiveIntRange;
import com.android.apksig.internal.util.MessageDigestSink;
import com.android.apksig.internal.zip.CentralDirectoryRecord;
import com.android.apksig.internal.zip.LocalFileRecord;
import com.android.apksig.util.DataSource;
import com.android.apksig.zip.ZipFormatException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.SignerInfo;

/**
 * APK verifier which uses JAR signing (aka v1 signing scheme).
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File">Signed JAR File</a>
 */
public abstract class V1SchemeVerifier {

    private static final String MANIFEST_ENTRY_NAME = V1SchemeSigner.MANIFEST_ENTRY_NAME;

    private V1SchemeVerifier() {}

    /**
     * Verifies the provided APK's JAR signatures and returns the result of verification. APK is
     * considered verified only if {@link Result#verified} is {@code true}. If verification fails,
     * the result will contain errors -- see {@link Result#getErrors()}.
     *
     * @throws ApkFormatException if the APK is malformed
     * @throws IOException if an I/O error occurs when reading the APK
     * @throws NoSuchAlgorithmException if the APK's JAR signatures cannot be verified because a
     *         required cryptographic algorithm implementation is missing
     */
    public static Result verify(
            DataSource apk,
            ApkUtils.ZipSections apkSections,
            Map<Integer, String> supportedApkSigSchemeNames,
            Set<Integer> foundApkSigSchemeIds,
            int minSdkVersion,
            int maxSdkVersion) throws IOException, ApkFormatException, NoSuchAlgorithmException {
        if (minSdkVersion > maxSdkVersion) {
            throw new IllegalArgumentException(
                    "minSdkVersion (" + minSdkVersion + ") > maxSdkVersion (" + maxSdkVersion
                            + ")");
        }

        Result result = new Result();

        // Parse the ZIP Central Directory and check that there are no entries with duplicate names.
        List<CentralDirectoryRecord> cdRecords = parseZipCentralDirectory(apk, apkSections);
        Set<String> cdEntryNames = checkForDuplicateEntries(cdRecords, result);
        if (result.containsErrors()) {
            return result;
        }

        // Verify JAR signature(s).
        Signers.verify(
                apk,
                apkSections.getZipCentralDirectoryOffset(),
                cdRecords,
                cdEntryNames,
                supportedApkSigSchemeNames,
                foundApkSigSchemeIds,
                minSdkVersion,
                maxSdkVersion,
                result);

        return result;
    }

    /**
     * Returns the set of entry names and reports any duplicate entry names in the {@code result}
     * as errors.
     */
    private static Set<String> checkForDuplicateEntries(
            List<CentralDirectoryRecord> cdRecords, Result result) {
        Set<String> cdEntryNames = new HashSet<>(cdRecords.size());
        Set<String> duplicateCdEntryNames = null;
        for (CentralDirectoryRecord cdRecord : cdRecords) {
            String entryName = cdRecord.getName();
            if (!cdEntryNames.add(entryName)) {
                // This is an error. Report this once per duplicate name.
                if (duplicateCdEntryNames == null) {
                    duplicateCdEntryNames = new HashSet<>();
                }
                if (duplicateCdEntryNames.add(entryName)) {
                    result.addError(Issue.JAR_SIG_DUPLICATE_ZIP_ENTRY, entryName);
                }
            }
        }
        return cdEntryNames;
    }

    /**
     * All JAR signers of an APK.
     */
    private static class Signers {

        /**
         * Verifies JAR signatures of the provided APK and populates the provided result container
         * with errors, warnings, and information about signers. The APK is considered verified if
         * the {@link Result#verified} is {@code true}.
         */
        private static void verify(
                DataSource apk,
                long cdStartOffset,
                List<CentralDirectoryRecord> cdRecords,
                Set<String> cdEntryNames,
                Map<Integer, String> supportedApkSigSchemeNames,
                Set<Integer> foundApkSigSchemeIds,
                int minSdkVersion,
                int maxSdkVersion,
                Result result) throws ApkFormatException, IOException, NoSuchAlgorithmException {

            // Find JAR manifest and signature block files.
            CentralDirectoryRecord manifestEntry = null;
            Map<String, CentralDirectoryRecord> sigFileEntries = new HashMap<>(1);
            List<CentralDirectoryRecord> sigBlockEntries = new ArrayList<>(1);
            for (CentralDirectoryRecord cdRecord : cdRecords) {
                String entryName = cdRecord.getName();
                if (!entryName.startsWith("META-INF/")) {
                    continue;
                }
                if ((manifestEntry == null) && (MANIFEST_ENTRY_NAME.equals(entryName))) {
                    manifestEntry = cdRecord;
                    continue;
                }
                if (entryName.endsWith(".SF")) {
                    sigFileEntries.put(entryName, cdRecord);
                    continue;
                }
                if ((entryName.endsWith(".RSA"))
                        || (entryName.endsWith(".DSA"))
                        || (entryName.endsWith(".EC"))) {
                    sigBlockEntries.add(cdRecord);
                    continue;
                }
            }
            if (manifestEntry == null) {
                result.addError(Issue.JAR_SIG_NO_MANIFEST);
                return;
            }

            // Parse the JAR manifest and check that all JAR entries it references exist in the APK.
            byte[] manifestBytes;
            try {
                manifestBytes =
                        LocalFileRecord.getUncompressedData(apk, manifestEntry, cdStartOffset);
            } catch (ZipFormatException e) {
                throw new ApkFormatException("Malformed ZIP entry: " + manifestEntry.getName(), e);
            }
            Map<String, ManifestParser.Section> entryNameToManifestSection = null;
            ManifestParser manifest = new ManifestParser(manifestBytes);
            ManifestParser.Section manifestMainSection = manifest.readSection();
            List<ManifestParser.Section> manifestIndividualSections = manifest.readAllSections();
            entryNameToManifestSection = new HashMap<>(manifestIndividualSections.size());
            int manifestSectionNumber = 0;
            for (ManifestParser.Section manifestSection : manifestIndividualSections) {
                manifestSectionNumber++;
                String entryName = manifestSection.getName();
                if (entryName == null) {
                    result.addError(Issue.JAR_SIG_UNNNAMED_MANIFEST_SECTION, manifestSectionNumber);
                    continue;
                }
                if (entryNameToManifestSection.put(entryName, manifestSection) != null) {
                    result.addError(Issue.JAR_SIG_DUPLICATE_MANIFEST_SECTION, entryName);
                    continue;
                }
                if (!cdEntryNames.contains(entryName)) {
                    result.addError(
                            Issue.JAR_SIG_MISSING_ZIP_ENTRY_REFERENCED_IN_MANIFEST, entryName);
                    continue;
                }
            }
            if (result.containsErrors()) {
                return;
            }
            // STATE OF AFFAIRS:
            // * All JAR entries listed in JAR manifest are present in the APK.

            // Identify signers
            List<Signer> signers = new ArrayList<>(sigBlockEntries.size());
            for (CentralDirectoryRecord sigBlockEntry : sigBlockEntries) {
                String sigBlockEntryName = sigBlockEntry.getName();
                int extensionDelimiterIndex = sigBlockEntryName.lastIndexOf('.');
                if (extensionDelimiterIndex == -1) {
                    throw new RuntimeException(
                            "Signature block file name does not contain extension: "
                                    + sigBlockEntryName);
                }
                String sigFileEntryName =
                        sigBlockEntryName.substring(0, extensionDelimiterIndex) + ".SF";
                CentralDirectoryRecord sigFileEntry = sigFileEntries.get(sigFileEntryName);
                if (sigFileEntry == null) {
                    result.addWarning(
                            Issue.JAR_SIG_MISSING_FILE, sigBlockEntryName, sigFileEntryName);
                    continue;
                }
                String signerName = sigBlockEntryName.substring("META-INF/".length());
                Result.SignerInfo signerInfo =
                        new Result.SignerInfo(
                                signerName, sigBlockEntryName, sigFileEntry.getName());
                Signer signer = new Signer(signerName, sigBlockEntry, sigFileEntry, signerInfo);
                signers.add(signer);
            }
            if (signers.isEmpty()) {
                result.addError(Issue.JAR_SIG_NO_SIGNATURES);
                return;
            }

            // Verify each signer's signature block file .(RSA|DSA|EC) against the corresponding
            // signature file .SF. Any error encountered for any signer terminates verification, to
            // mimic Android's behavior.
            for (Signer signer : signers) {
                signer.verifySigBlockAgainstSigFile(
                        apk, cdStartOffset, minSdkVersion, maxSdkVersion);
                if (signer.getResult().containsErrors()) {
                    result.signers.add(signer.getResult());
                }
            }
            if (result.containsErrors()) {
                return;
            }
            // STATE OF AFFAIRS:
            // * All JAR entries listed in JAR manifest are present in the APK.
            // * All signature files (.SF) verify against corresponding block files (.RSA|.DSA|.EC).

            // Verify each signer's signature file (.SF) against the JAR manifest.
            List<Signer> remainingSigners = new ArrayList<>(signers.size());
            for (Signer signer : signers) {
                signer.verifySigFileAgainstManifest(
                        manifestBytes,
                        manifestMainSection,
                        entryNameToManifestSection,
                        supportedApkSigSchemeNames,
                        foundApkSigSchemeIds,
                        minSdkVersion,
                        maxSdkVersion);
                if (signer.isIgnored()) {
                    result.ignoredSigners.add(signer.getResult());
                } else {
                    if (signer.getResult().containsErrors()) {
                        result.signers.add(signer.getResult());
                    } else {
                        remainingSigners.add(signer);
                    }
                }
            }
            if (result.containsErrors()) {
                return;
            }
            signers = remainingSigners;
            if (signers.isEmpty()) {
                result.addError(Issue.JAR_SIG_NO_SIGNATURES);
                return;
            }
            // STATE OF AFFAIRS:
            // * All signature files (.SF) verify against corresponding block files (.RSA|.DSA|.EC).
            // * Contents of all JAR manifest sections listed in .SF files verify against .SF files.
            // * All JAR entries listed in JAR manifest are present in the APK.

            // Verify data of JAR entries against JAR manifest and .SF files. On Android, an APK's
            // JAR entry is considered signed by signers associated with an .SF file iff the entry
            // is mentioned in the .SF file and the entry's digest(s) mentioned in the JAR manifest
            // match theentry's uncompressed data. Android requires that all such JAR entries are
            // signed by the same set of signers. This set may be smaller than the set of signers
            // we've identified so far.
            Set<Signer> apkSigners =
                    verifyJarEntriesAgainstManifestAndSigners(
                            apk,
                            cdStartOffset,
                            cdRecords,
                            entryNameToManifestSection,
                            signers,
                            minSdkVersion,
                            maxSdkVersion,
                            result);
            if (result.containsErrors()) {
                return;
            }
            // STATE OF AFFAIRS:
            // * All signature files (.SF) verify against corresponding block files (.RSA|.DSA|.EC).
            // * Contents of all JAR manifest sections listed in .SF files verify against .SF files.
            // * All JAR entries listed in JAR manifest are present in the APK.
            // * All JAR entries present in the APK and supposed to be covered by JAR signature
            //   (i.e., reside outside of META-INF/) are covered by signatures from the same set
            //   of signers.

            // Report any JAR entries which aren't covered by signature.
            Set<String> signatureEntryNames = new HashSet<>(1 + result.signers.size() * 2);
            signatureEntryNames.add(manifestEntry.getName());
            for (Signer signer : apkSigners) {
                signatureEntryNames.add(signer.getSignatureBlockEntryName());
                signatureEntryNames.add(signer.getSignatureFileEntryName());
            }
            for (CentralDirectoryRecord cdRecord : cdRecords) {
                String entryName = cdRecord.getName();
                if ((entryName.startsWith("META-INF/"))
                        && (!entryName.endsWith("/"))
                        && (!signatureEntryNames.contains(entryName))) {
                    result.addWarning(Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY, entryName);
                }
            }

            // Reflect the sets of used signers and ignored signers in the result.
            for (Signer signer : signers) {
                if (apkSigners.contains(signer)) {
                    result.signers.add(signer.getResult());
                } else {
                    result.ignoredSigners.add(signer.getResult());
                }
            }

            result.verified = true;
        }
    }

    private static class Signer {
        private final String mName;
        private final Result.SignerInfo mResult;
        private final CentralDirectoryRecord mSignatureFileEntry;
        private final CentralDirectoryRecord mSignatureBlockEntry;
        private boolean mIgnored;

        private byte[] mSigFileBytes;
        private Set<String> mSigFileEntryNames;

        private Signer(
                String name,
                CentralDirectoryRecord sigBlockEntry,
                CentralDirectoryRecord sigFileEntry,
                Result.SignerInfo result) {
            mName = name;
            mResult = result;
            mSignatureBlockEntry = sigBlockEntry;
            mSignatureFileEntry = sigFileEntry;
        }

        public String getName() {
            return mName;
        }

        public String getSignatureFileEntryName() {
            return mSignatureFileEntry.getName();
        }

        public String getSignatureBlockEntryName() {
            return mSignatureBlockEntry.getName();
        }

        void setIgnored() {
            mIgnored = true;
        }

        public boolean isIgnored() {
            return mIgnored;
        }

        public Set<String> getSigFileEntryNames() {
            return mSigFileEntryNames;
        }

        public Result.SignerInfo getResult() {
            return mResult;
        }

        @SuppressWarnings("restriction")
        public void verifySigBlockAgainstSigFile(
                DataSource apk, long cdStartOffset, int minSdkVersion, int maxSdkVersion)
                        throws IOException, ApkFormatException, NoSuchAlgorithmException {
            byte[] sigBlockBytes;
            try {
                sigBlockBytes =
                        LocalFileRecord.getUncompressedData(
                                apk, mSignatureBlockEntry, cdStartOffset);
            } catch (ZipFormatException e) {
                throw new ApkFormatException(
                        "Malformed ZIP entry: " + mSignatureBlockEntry.getName(), e);
            }
            try {
                mSigFileBytes =
                        LocalFileRecord.getUncompressedData(
                                apk, mSignatureFileEntry, cdStartOffset);
            } catch (ZipFormatException e) {
                throw new ApkFormatException(
                        "Malformed ZIP entry: " + mSignatureFileEntry.getName(), e);
            }
            PKCS7 sigBlock;
            try {
                sigBlock = new PKCS7(sigBlockBytes);
            } catch (IOException e) {
                if (e.getCause() instanceof CertificateException) {
                    mResult.addError(
                            Issue.JAR_SIG_MALFORMED_CERTIFICATE, mSignatureBlockEntry.getName(), e);
                } else {
                    mResult.addError(
                            Issue.JAR_SIG_PARSE_EXCEPTION, mSignatureBlockEntry.getName(), e);
                }
                return;
            }
            SignerInfo[] unverifiedSignerInfos = sigBlock.getSignerInfos();
            if ((unverifiedSignerInfos == null) || (unverifiedSignerInfos.length == 0)) {
                mResult.addError(Issue.JAR_SIG_NO_SIGNERS, mSignatureBlockEntry.getName());
                return;
            }

            SignerInfo verifiedSignerInfo = null;
            if ((unverifiedSignerInfos != null) && (unverifiedSignerInfos.length > 0)) {
                for (int i = 0; i < unverifiedSignerInfos.length; i++) {
                    SignerInfo unverifiedSignerInfo = unverifiedSignerInfos[i];
                    String digestAlgorithmOid =
                            unverifiedSignerInfo.getDigestAlgorithmId().getOID().toString();
                    String signatureAlgorithmOid =
                            unverifiedSignerInfo
                                    .getDigestEncryptionAlgorithmId().getOID().toString();
                    InclusiveIntRange desiredApiLevels =
                            InclusiveIntRange.fromTo(minSdkVersion, maxSdkVersion);
                    List<InclusiveIntRange> apiLevelsWhereDigestAndSigAlgorithmSupported =
                            getSigAlgSupportedApiLevels(digestAlgorithmOid, signatureAlgorithmOid);
                    List<InclusiveIntRange> apiLevelsWhereDigestAlgorithmNotSupported =
                            desiredApiLevels.getValuesNotIn(apiLevelsWhereDigestAndSigAlgorithmSupported);
                    if (!apiLevelsWhereDigestAlgorithmNotSupported.isEmpty()) {
                        mResult.addError(
                                Issue.JAR_SIG_UNSUPPORTED_SIG_ALG,
                                mSignatureBlockEntry.getName(),
                                digestAlgorithmOid,
                                signatureAlgorithmOid,
                                String.valueOf(apiLevelsWhereDigestAlgorithmNotSupported));
                        return;
                    }
                    try {
                        verifiedSignerInfo = sigBlock.verify(unverifiedSignerInfo, mSigFileBytes);
                    } catch (SignatureException e) {
                        mResult.addError(
                                Issue.JAR_SIG_VERIFY_EXCEPTION,
                                mSignatureBlockEntry.getName(),
                                mSignatureFileEntry.getName(),
                                e);
                        return;
                    }
                    if (verifiedSignerInfo != null) {
                        // Verified
                        break;
                    }

                    // Did not verify
                    if (minSdkVersion < AndroidSdkVersion.N) {
                        // Prior to N, Android attempted to verify only the first SignerInfo.
                        mResult.addError(
                                Issue.JAR_SIG_DID_NOT_VERIFY,
                                mSignatureBlockEntry.getName(),
                                mSignatureFileEntry.getName());
                        return;
                    }
                }
            }
            if (verifiedSignerInfo == null) {
                mResult.addError(Issue.JAR_SIG_NO_SIGNERS, mSignatureBlockEntry.getName());
                return;
            }

            // TODO: PKCS7 class doesn't guarantee that returned certificates' getEncoded returns
            // the original encoded form of certificates rather than the DER re-encoded form. We
            // need to replace the PKCS7 parser/verifier.
            List<X509Certificate> certChain;
            try {
                certChain = verifiedSignerInfo.getCertificateChain(sigBlock);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to obtain cert chain from " + mSignatureBlockEntry.getName(), e);
            }
            if ((certChain == null) || (certChain.isEmpty())) {
                throw new RuntimeException("Verified SignerInfo does not have a certificate chain");
            }
            mResult.certChain.clear();
            mResult.certChain.addAll(certChain);
        }

        private static final String OID_DIGEST_MD5 = "1.2.840.113549.2.5";
        private static final String OID_DIGEST_SHA1 = "1.3.14.3.2.26";
        private static final String OID_DIGEST_SHA224 = "2.16.840.1.101.3.4.2.4";
        private static final String OID_DIGEST_SHA256 = "2.16.840.1.101.3.4.2.1";
        private static final String OID_DIGEST_SHA384 = "2.16.840.1.101.3.4.2.2";
        private static final String OID_DIGEST_SHA512 = "2.16.840.1.101.3.4.2.3";

        private static final String OID_SIG_RSA = "1.2.840.113549.1.1.1";
        private static final String OID_SIG_MD5_WITH_RSA = "1.2.840.113549.1.1.4";
        private static final String OID_SIG_SHA1_WITH_RSA = "1.2.840.113549.1.1.5";
        private static final String OID_SIG_SHA224_WITH_RSA = "1.2.840.113549.1.1.14";
        private static final String OID_SIG_SHA256_WITH_RSA = "1.2.840.113549.1.1.11";
        private static final String OID_SIG_SHA384_WITH_RSA = "1.2.840.113549.1.1.12";
        private static final String OID_SIG_SHA512_WITH_RSA = "1.2.840.113549.1.1.13";

        private static final String OID_SIG_DSA = "1.2.840.10040.4.1";
        private static final String OID_SIG_SHA1_WITH_DSA = "1.2.840.10040.4.3";
        private static final String OID_SIG_SHA224_WITH_DSA = "2.16.840.1.101.3.4.3.1";
        private static final String OID_SIG_SHA256_WITH_DSA = "2.16.840.1.101.3.4.3.2";

        private static final String OID_SIG_EC_PUBLIC_KEY = "1.2.840.10045.2.1";
        private static final String OID_SIG_SHA1_WITH_ECDSA = "1.2.840.10045.4.1";
        private static final String OID_SIG_SHA224_WITH_ECDSA = "1.2.840.10045.4.3.1";
        private static final String OID_SIG_SHA256_WITH_ECDSA = "1.2.840.10045.4.3.2";
        private static final String OID_SIG_SHA384_WITH_ECDSA = "1.2.840.10045.4.3.3";
        private static final String OID_SIG_SHA512_WITH_ECDSA = "1.2.840.10045.4.3.4";

        private static final Map<String, List<InclusiveIntRange>> SUPPORTED_SIG_ALG_OIDS =
                new HashMap<>();
        {
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_RSA,
                    InclusiveIntRange.from(0));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_MD5_WITH_RSA,
                    InclusiveIntRange.fromTo(0, 8), InclusiveIntRange.from(21));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA1_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA224_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA256_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA384_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA512_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_RSA,
                    InclusiveIntRange.from(0));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_MD5_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA1_WITH_RSA,
                    InclusiveIntRange.from(0));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA224_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA256_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA384_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA512_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_RSA,
                    InclusiveIntRange.fromTo(0, 8), InclusiveIntRange.from(21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_MD5_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA1_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA224_WITH_RSA,
                    InclusiveIntRange.fromTo(0, 8), InclusiveIntRange.from(21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA256_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA384_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA512_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_RSA,
                    InclusiveIntRange.fromTo(0, 8), InclusiveIntRange.from(18));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_MD5_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA1_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA224_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA256_WITH_RSA,
                    InclusiveIntRange.fromTo(0, 8), InclusiveIntRange.from(18));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA384_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA512_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_RSA,
                    InclusiveIntRange.from(18));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_MD5_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA1_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA224_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA256_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA384_WITH_RSA,
                    InclusiveIntRange.from(21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA512_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_RSA,
                    InclusiveIntRange.from(18));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_MD5_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA1_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA224_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA256_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA384_WITH_RSA,
                    InclusiveIntRange.fromTo(21, 21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA512_WITH_RSA,
                    InclusiveIntRange.from(21));

            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA1_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA224_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA256_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_DSA,
                    InclusiveIntRange.from(0));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA1_WITH_DSA,
                    InclusiveIntRange.from(9));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA224_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA256_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_DSA,
                    InclusiveIntRange.from(22));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA1_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA224_WITH_DSA,
                    InclusiveIntRange.from(21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA256_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_DSA,
                    InclusiveIntRange.from(22));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA1_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA224_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA256_WITH_DSA,
                    InclusiveIntRange.from(21));

            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA1_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA224_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA256_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA1_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA224_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA256_WITH_DSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_EC_PUBLIC_KEY,
                    InclusiveIntRange.from(18));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_EC_PUBLIC_KEY,
                    InclusiveIntRange.from(21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_EC_PUBLIC_KEY,
                    InclusiveIntRange.from(18));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_EC_PUBLIC_KEY,
                    InclusiveIntRange.from(18));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_EC_PUBLIC_KEY,
                    InclusiveIntRange.from(18));

            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA1_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA224_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA256_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA384_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_MD5, OID_SIG_SHA512_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA1_WITH_ECDSA,
                    InclusiveIntRange.from(18));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA224_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA256_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA384_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA1, OID_SIG_SHA512_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA1_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA224_WITH_ECDSA,
                    InclusiveIntRange.from(21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA256_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA384_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA224, OID_SIG_SHA512_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA1_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA224_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA256_WITH_ECDSA,
                    InclusiveIntRange.from(21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA384_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA256, OID_SIG_SHA512_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA1_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA224_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA256_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA384_WITH_ECDSA,
                    InclusiveIntRange.from(21));
            addSupportedSigAlg(
                    OID_DIGEST_SHA384, OID_SIG_SHA512_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));

            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA1_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA224_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA256_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA384_WITH_ECDSA,
                    InclusiveIntRange.fromTo(21, 23));
            addSupportedSigAlg(
                    OID_DIGEST_SHA512, OID_SIG_SHA512_WITH_ECDSA,
                    InclusiveIntRange.from(21));
        }

        private static void addSupportedSigAlg(
                String digestAlgorithmOid,
                String signatureAlgorithmOid,
                InclusiveIntRange... supportedApiLevels) {
            SUPPORTED_SIG_ALG_OIDS.put(
                    digestAlgorithmOid + "with" + signatureAlgorithmOid,
                    Arrays.asList(supportedApiLevels));
        }

        private List<InclusiveIntRange> getSigAlgSupportedApiLevels(
                String digestAlgorithmOid,
                String signatureAlgorithmOid) {
            List<InclusiveIntRange> result =
                    SUPPORTED_SIG_ALG_OIDS.get(digestAlgorithmOid + "with" + signatureAlgorithmOid);
            return (result != null) ? result : Collections.emptyList();
        }

        public void verifySigFileAgainstManifest(
                byte[] manifestBytes,
                ManifestParser.Section manifestMainSection,
                Map<String, ManifestParser.Section> entryNameToManifestSection,
                Map<Integer, String> supportedApkSigSchemeNames,
                Set<Integer> foundApkSigSchemeIds,
                int minSdkVersion,
                int maxSdkVersion) throws NoSuchAlgorithmException {
            // Inspect the main section of the .SF file.
            ManifestParser sf = new ManifestParser(mSigFileBytes);
            ManifestParser.Section sfMainSection = sf.readSection();
            if (sfMainSection.getAttributeValue(Attributes.Name.SIGNATURE_VERSION) == null) {
                mResult.addError(
                        Issue.JAR_SIG_MISSING_VERSION_ATTR_IN_SIG_FILE,
                        mSignatureFileEntry.getName());
                setIgnored();
                return;
            }

            if (maxSdkVersion >= AndroidSdkVersion.N) {
                // Android N and newer rejects APKs whose .SF file says they were supposed to be
                // signed with APK Signature Scheme v2 (or newer) and yet no such signature was
                // found.
                checkForStrippedApkSignatures(
                        sfMainSection, supportedApkSigSchemeNames, foundApkSigSchemeIds);
                if (mResult.containsErrors()) {
                    return;
                }
            }

            boolean createdBySigntool = false;
            String createdBy = sfMainSection.getAttributeValue("Created-By");
            if (createdBy != null) {
                createdBySigntool = createdBy.indexOf("signtool") != -1;
            }
            boolean manifestDigestVerified =
                    verifyManifestDigest(
                            sfMainSection,
                            createdBySigntool,
                            manifestBytes,
                            minSdkVersion,
                            maxSdkVersion);
            if (!createdBySigntool) {
                verifyManifestMainSectionDigest(
                        sfMainSection,
                        manifestMainSection,
                        manifestBytes,
                        minSdkVersion,
                        maxSdkVersion);
            }
            if (mResult.containsErrors()) {
                return;
            }

            // Inspect per-entry sections of .SF file. Technically, if the digest of JAR manifest
            // verifies, per-entry sections should be ignored. However, most Android platform
            // implementations require that such sections exist.
            List<ManifestParser.Section> sfSections = sf.readAllSections();
            Set<String> sfEntryNames = new HashSet<>(sfSections.size());
            int sfSectionNumber = 0;
            for (ManifestParser.Section sfSection : sfSections) {
                sfSectionNumber++;
                String entryName = sfSection.getName();
                if (entryName == null) {
                    mResult.addError(
                            Issue.JAR_SIG_UNNNAMED_SIG_FILE_SECTION,
                            mSignatureFileEntry.getName(),
                            sfSectionNumber);
                    setIgnored();
                    return;
                }
                if (!sfEntryNames.add(entryName)) {
                    mResult.addError(
                            Issue.JAR_SIG_DUPLICATE_SIG_FILE_SECTION,
                            mSignatureFileEntry.getName(),
                            entryName);
                    setIgnored();
                    return;
                }
                if (manifestDigestVerified) {
                    // No need to verify this entry's corresponding JAR manifest entry because the
                    // JAR manifest verifies in full.
                    continue;
                }
                // Whole-file digest of JAR manifest hasn't been verified. Thus, we need to verify
                // the digest of the JAR manifest section corresponding to this .SF section.
                ManifestParser.Section manifestSection = entryNameToManifestSection.get(entryName);
                if (manifestSection == null) {
                    mResult.addError(
                            Issue.JAR_SIG_NO_ZIP_ENTRY_DIGEST_IN_SIG_FILE,
                            entryName,
                            mSignatureFileEntry.getName());
                    setIgnored();
                    continue;
                }
                verifyManifestIndividualSectionDigest(
                        sfSection,
                        createdBySigntool,
                        manifestSection,
                        manifestBytes,
                        minSdkVersion,
                        maxSdkVersion);
            }
            mSigFileEntryNames = sfEntryNames;
        }


        /**
         * Returns {@code true} if the whole-file digest of the manifest against the main section of
         * the .SF file.
         */
        private boolean verifyManifestDigest(
                ManifestParser.Section sfMainSection,
                boolean createdBySigntool,
                byte[] manifestBytes,
                int minSdkVersion,
                int maxSdkVersion) throws NoSuchAlgorithmException {
            Collection<NamedDigest> expectedDigests =
                    getDigestsToVerify(
                            sfMainSection,
                            ((createdBySigntool) ? "-Digest" : "-Digest-Manifest"),
                            minSdkVersion,
                            maxSdkVersion);
            boolean digestFound = !expectedDigests.isEmpty();
            if (!digestFound) {
                mResult.addWarning(
                        Issue.JAR_SIG_NO_MANIFEST_DIGEST_IN_SIG_FILE,
                        mSignatureFileEntry.getName());
                return false;
            }

            boolean verified = true;
            for (NamedDigest expectedDigest : expectedDigests) {
                String jcaDigestAlgorithm = expectedDigest.jcaDigestAlgorithm;
                byte[] actual = digest(jcaDigestAlgorithm, manifestBytes);
                byte[] expected = expectedDigest.digest;
                if (!Arrays.equals(expected, actual)) {
                    mResult.addWarning(
                            Issue.JAR_SIG_ZIP_ENTRY_DIGEST_DID_NOT_VERIFY,
                            V1SchemeSigner.MANIFEST_ENTRY_NAME,
                            jcaDigestAlgorithm,
                            mSignatureFileEntry.getName(),
                            Base64.getEncoder().encodeToString(actual),
                            Base64.getEncoder().encodeToString(expected));
                    verified = false;
                }
            }
            return verified;
        }

        /**
         * Verifies the digest of the manifest's main section against the main section of the .SF
         * file.
         */
        private void verifyManifestMainSectionDigest(
                ManifestParser.Section sfMainSection,
                ManifestParser.Section manifestMainSection,
                byte[] manifestBytes,
                int minSdkVersion,
                int maxSdkVersion) throws NoSuchAlgorithmException {
            Collection<NamedDigest> expectedDigests =
                    getDigestsToVerify(
                            sfMainSection,
                            "-Digest-Manifest-Main-Attributes",
                            minSdkVersion,
                            maxSdkVersion);
            if (expectedDigests.isEmpty()) {
                return;
            }

            for (NamedDigest expectedDigest : expectedDigests) {
                String jcaDigestAlgorithm = expectedDigest.jcaDigestAlgorithm;
                byte[] actual =
                        digest(
                                jcaDigestAlgorithm,
                                manifestBytes,
                                manifestMainSection.getStartOffset(),
                                manifestMainSection.getSizeBytes());
                byte[] expected = expectedDigest.digest;
                if (!Arrays.equals(expected, actual)) {
                    mResult.addError(
                            Issue.JAR_SIG_MANIFEST_MAIN_SECTION_DIGEST_DID_NOT_VERIFY,
                            jcaDigestAlgorithm,
                            mSignatureFileEntry.getName(),
                            Base64.getEncoder().encodeToString(actual),
                            Base64.getEncoder().encodeToString(expected));
                }
            }
        }

        /**
         * Verifies the digest of the manifest's individual section against the corresponding
         * individual section of the .SF file.
         */
        private void verifyManifestIndividualSectionDigest(
                ManifestParser.Section sfIndividualSection,
                boolean createdBySigntool,
                ManifestParser.Section manifestIndividualSection,
                byte[] manifestBytes,
                int minSdkVersion,
                int maxSdkVersion) throws NoSuchAlgorithmException {
            String entryName = sfIndividualSection.getName();
            Collection<NamedDigest> expectedDigests =
                    getDigestsToVerify(
                            sfIndividualSection, "-Digest", minSdkVersion, maxSdkVersion);
            if (expectedDigests.isEmpty()) {
                mResult.addError(
                        Issue.JAR_SIG_NO_ZIP_ENTRY_DIGEST_IN_SIG_FILE,
                        entryName,
                        mSignatureFileEntry.getName());
                return;
            }

            int sectionStartIndex = manifestIndividualSection.getStartOffset();
            int sectionSizeBytes = manifestIndividualSection.getSizeBytes();
            if (createdBySigntool) {
                int sectionEndIndex = sectionStartIndex + sectionSizeBytes;
                if ((manifestBytes[sectionEndIndex - 1] == '\n')
                        && (manifestBytes[sectionEndIndex - 2] == '\n')) {
                    sectionSizeBytes--;
                }
            }
            for (NamedDigest expectedDigest : expectedDigests) {
                String jcaDigestAlgorithm = expectedDigest.jcaDigestAlgorithm;
                byte[] actual =
                        digest(
                                jcaDigestAlgorithm,
                                manifestBytes,
                                sectionStartIndex,
                                sectionSizeBytes);
                byte[] expected = expectedDigest.digest;
                if (!Arrays.equals(expected, actual)) {
                    mResult.addError(
                            Issue.JAR_SIG_MANIFEST_SECTION_DIGEST_DID_NOT_VERIFY,
                            entryName,
                            jcaDigestAlgorithm,
                            mSignatureFileEntry.getName(),
                            Base64.getEncoder().encodeToString(actual),
                            Base64.getEncoder().encodeToString(expected));
                }
            }
        }

        private void checkForStrippedApkSignatures(
                ManifestParser.Section sfMainSection,
                Map<Integer, String> supportedApkSigSchemeNames,
                Set<Integer> foundApkSigSchemeIds) {
            String signedWithApkSchemes =
                    sfMainSection.getAttributeValue(
                            V1SchemeSigner.SF_ATTRIBUTE_NAME_ANDROID_APK_SIGNED_NAME_STR);
            // This field contains a comma-separated list of APK signature scheme IDs which were
            // used to sign this APK. Android rejects APKs where an ID is known to the platform but
            // the APK didn't verify using that scheme.

            if (signedWithApkSchemes == null) {
                // APK signature (e.g., v2 scheme) stripping protections not enabled.
                if (!foundApkSigSchemeIds.isEmpty()) {
                    // APK is signed with an APK signature scheme such as v2 scheme.
                    mResult.addWarning(
                            Issue.JAR_SIG_NO_APK_SIG_STRIP_PROTECTION,
                            mSignatureFileEntry.getName());
                }
                return;
            }

            if (supportedApkSigSchemeNames.isEmpty()) {
                return;
            }

            Set<Integer> supportedApkSigSchemeIds = supportedApkSigSchemeNames.keySet();
            Set<Integer> supportedExpectedApkSigSchemeIds = new HashSet<>(1);
            StringTokenizer tokenizer = new StringTokenizer(signedWithApkSchemes, ",");
            while (tokenizer.hasMoreTokens()) {
                String idText = tokenizer.nextToken().trim();
                if (idText.isEmpty()) {
                    continue;
                }
                int id;
                try {
                    id = Integer.parseInt(idText);
                } catch (Exception ignored) {
                    continue;
                }
                // This APK was supposed to be signed with the APK signature scheme having
                // this ID.
                if (supportedApkSigSchemeIds.contains(id)) {
                    supportedExpectedApkSigSchemeIds.add(id);
                } else {
                    mResult.addWarning(
                            Issue.JAR_SIG_UNKNOWN_APK_SIG_SCHEME_ID,
                            mSignatureFileEntry.getName(),
                            id);
                }
            }

            for (int id : supportedExpectedApkSigSchemeIds) {
                if (!foundApkSigSchemeIds.contains(id)) {
                    String apkSigSchemeName = supportedApkSigSchemeNames.get(id);
                    mResult.addError(
                            Issue.JAR_SIG_MISSING_APK_SIG_REFERENCED,
                            mSignatureFileEntry.getName(),
                            id,
                            apkSigSchemeName);
                }
            }
        }
    }

    private static Collection<NamedDigest> getDigestsToVerify(
            ManifestParser.Section section,
            String digestAttrSuffix,
            int minSdkVersion,
            int maxSdkVersion) {
        Decoder base64Decoder = Base64.getDecoder();
        List<NamedDigest> result = new ArrayList<>(1);
        if (minSdkVersion < AndroidSdkVersion.JELLY_BEAN_MR2) {
            // Prior to JB MR2, Android platform's logic for picking a digest algorithm to verify is
            // to rely on the ancient Digest-Algorithms attribute which contains
            // whitespace-separated list of digest algorithms (defaulting to SHA-1) to try. The
            // first digest attribute (with supported digest algorithm) found using the list is
            // used.
            String algs = section.getAttributeValue("Digest-Algorithms");
            if (algs == null) {
                algs = "SHA SHA1";
            }
            StringTokenizer tokens = new StringTokenizer(algs);
            while (tokens.hasMoreTokens()) {
                String alg = tokens.nextToken();
                String attrName = alg + digestAttrSuffix;
                String digestBase64 = section.getAttributeValue(attrName);
                if (digestBase64 == null) {
                    // Attribute not found
                    continue;
                }
                alg = getCanonicalJcaMessageDigestAlgorithm(alg);
                if ((alg == null)
                        || (getMinSdkVersionFromWhichSupportedInManifestOrSignatureFile(alg)
                                > minSdkVersion)) {
                    // Unsupported digest algorithm
                    continue;
                }
                // Supported digest algorithm
                result.add(new NamedDigest(alg, base64Decoder.decode(digestBase64)));
                break;
            }
            // No supported digests found -- this will fail to verify on pre-JB MR2 Androids.
            if (result.isEmpty()) {
                return result;
            }
        }

        if (maxSdkVersion >= AndroidSdkVersion.JELLY_BEAN_MR2) {
            // On JB MR2 and newer, Android platform picks the strongest algorithm out of:
            // SHA-512, SHA-384, SHA-256, SHA-1.
            for (String alg : JB_MR2_AND_NEWER_DIGEST_ALGS) {
                String attrName = getJarDigestAttributeName(alg, digestAttrSuffix);
                String digestBase64 = section.getAttributeValue(attrName);
                if (digestBase64 == null) {
                    // Attribute not found
                    continue;
                }
                byte[] digest = base64Decoder.decode(digestBase64);
                byte[] digestInResult = getDigest(result, alg);
                if ((digestInResult == null) || (!Arrays.equals(digestInResult, digest))) {
                    result.add(new NamedDigest(alg, digest));
                }
                break;
            }
        }

        return result;
    }

    private static final String[] JB_MR2_AND_NEWER_DIGEST_ALGS = {
            "SHA-512",
            "SHA-384",
            "SHA-256",
            "SHA-1",
    };

    private static String getCanonicalJcaMessageDigestAlgorithm(String algorithm) {
        return UPPER_CASE_JCA_DIGEST_ALG_TO_CANONICAL.get(algorithm.toUpperCase(Locale.US));
    }

    public static int getMinSdkVersionFromWhichSupportedInManifestOrSignatureFile(
            String jcaAlgorithmName) {
        Integer result =
                MIN_SDK_VESION_FROM_WHICH_DIGEST_SUPPORTED_IN_MANIFEST.get(
                        jcaAlgorithmName.toUpperCase(Locale.US));
        return (result != null) ? result : Integer.MAX_VALUE;
    }

    private static String getJarDigestAttributeName(
            String jcaDigestAlgorithm, String attrNameSuffix) {
        if ("SHA-1".equalsIgnoreCase(jcaDigestAlgorithm)) {
            return "SHA1" + attrNameSuffix;
        } else {
            return jcaDigestAlgorithm + attrNameSuffix;
        }
    }

    private static final Map<String, String> UPPER_CASE_JCA_DIGEST_ALG_TO_CANONICAL;
    static {
        UPPER_CASE_JCA_DIGEST_ALG_TO_CANONICAL = new HashMap<>(8);
        UPPER_CASE_JCA_DIGEST_ALG_TO_CANONICAL.put("MD5", "MD5");
        UPPER_CASE_JCA_DIGEST_ALG_TO_CANONICAL.put("SHA", "SHA-1");
        UPPER_CASE_JCA_DIGEST_ALG_TO_CANONICAL.put("SHA1", "SHA-1");
        UPPER_CASE_JCA_DIGEST_ALG_TO_CANONICAL.put("SHA-1", "SHA-1");
        UPPER_CASE_JCA_DIGEST_ALG_TO_CANONICAL.put("SHA-256", "SHA-256");
        UPPER_CASE_JCA_DIGEST_ALG_TO_CANONICAL.put("SHA-384", "SHA-384");
        UPPER_CASE_JCA_DIGEST_ALG_TO_CANONICAL.put("SHA-512", "SHA-512");
    }

    private static final Map<String, Integer>
            MIN_SDK_VESION_FROM_WHICH_DIGEST_SUPPORTED_IN_MANIFEST;
    static {
        MIN_SDK_VESION_FROM_WHICH_DIGEST_SUPPORTED_IN_MANIFEST = new HashMap<>(5);
        MIN_SDK_VESION_FROM_WHICH_DIGEST_SUPPORTED_IN_MANIFEST.put("MD5", 0);
        MIN_SDK_VESION_FROM_WHICH_DIGEST_SUPPORTED_IN_MANIFEST.put("SHA-1", 0);
        MIN_SDK_VESION_FROM_WHICH_DIGEST_SUPPORTED_IN_MANIFEST.put("SHA-256", 0);
        MIN_SDK_VESION_FROM_WHICH_DIGEST_SUPPORTED_IN_MANIFEST.put(
                "SHA-384", AndroidSdkVersion.GINGERBREAD);
        MIN_SDK_VESION_FROM_WHICH_DIGEST_SUPPORTED_IN_MANIFEST.put(
                "SHA-512", AndroidSdkVersion.GINGERBREAD);
    }

    private static byte[] getDigest(Collection<NamedDigest> digests, String jcaDigestAlgorithm) {
        for (NamedDigest digest : digests) {
            if (digest.jcaDigestAlgorithm.equalsIgnoreCase(jcaDigestAlgorithm)) {
                return digest.digest;
            }
        }
        return null;
    }

    public static List<CentralDirectoryRecord> parseZipCentralDirectory(
            DataSource apk,
            ApkUtils.ZipSections apkSections)
                    throws IOException, ApkFormatException {
        // Read the ZIP Central Directory
        long cdSizeBytes = apkSections.getZipCentralDirectorySizeBytes();
        if (cdSizeBytes > Integer.MAX_VALUE) {
            throw new ApkFormatException("ZIP Central Directory too large: " + cdSizeBytes);
        }
        long cdOffset = apkSections.getZipCentralDirectoryOffset();
        ByteBuffer cd = apk.getByteBuffer(cdOffset, (int) cdSizeBytes);
        cd.order(ByteOrder.LITTLE_ENDIAN);

        // Parse the ZIP Central Directory
        int expectedCdRecordCount = apkSections.getZipCentralDirectoryRecordCount();
        List<CentralDirectoryRecord> cdRecords = new ArrayList<>(expectedCdRecordCount);
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
            if (entryName.endsWith("/")) {
                // Ignore directory entries
                continue;
            }
            cdRecords.add(cdRecord);
        }
        // There may be more data in Central Directory, but we don't warn or throw because Android
        // ignores unused CD data.

        return cdRecords;
    }

    /**
     * Returns {@code true} if the provided JAR entry must be mentioned in signed JAR archive's
     * manifest for the APK to verify on Android.
     */
    private static boolean isJarEntryDigestNeededInManifest(String entryName) {
        // NOTE: This logic is different from what's required by the JAR signing scheme. This is
        // because Android's APK verification logic differs from that spec. In particular, JAR
        // signing spec includes into JAR manifest all files in subdirectories of META-INF and
        // any files inside META-INF not related to signatures.
        if (entryName.startsWith("META-INF/")) {
            return false;
        }
        return !entryName.endsWith("/");
    }

    private static Set<Signer> verifyJarEntriesAgainstManifestAndSigners(
            DataSource apk,
            long cdOffsetInApk,
            Collection<CentralDirectoryRecord> cdRecords,
            Map<String, ManifestParser.Section> entryNameToManifestSection,
            List<Signer> signers,
            int minSdkVersion,
            int maxSdkVersion,
            Result result) throws ApkFormatException, IOException, NoSuchAlgorithmException {
        // Iterate over APK contents as sequentially as possible to improve performance.
        List<CentralDirectoryRecord> cdRecordsSortedByLocalFileHeaderOffset =
                new ArrayList<>(cdRecords);
        Collections.sort(
                cdRecordsSortedByLocalFileHeaderOffset,
                CentralDirectoryRecord.BY_LOCAL_FILE_HEADER_OFFSET_COMPARATOR);
        Set<String> manifestEntryNamesMissingFromApk =
                new HashSet<>(entryNameToManifestSection.keySet());
        List<Signer> firstSignedEntrySigners = null;
        String firstSignedEntryName = null;
        for (CentralDirectoryRecord cdRecord : cdRecordsSortedByLocalFileHeaderOffset) {
            String entryName = cdRecord.getName();
            manifestEntryNamesMissingFromApk.remove(entryName);
            if (!isJarEntryDigestNeededInManifest(entryName)) {
                continue;
            }

            ManifestParser.Section manifestSection = entryNameToManifestSection.get(entryName);
            if (manifestSection == null) {
                result.addError(Issue.JAR_SIG_NO_ZIP_ENTRY_DIGEST_IN_MANIFEST, entryName);
                continue;
            }

            List<Signer> entrySigners = new ArrayList<>(signers.size());
            for (Signer signer : signers) {
                if (signer.getSigFileEntryNames().contains(entryName)) {
                    entrySigners.add(signer);
                }
            }
            if (entrySigners.isEmpty()) {
                result.addError(Issue.JAR_SIG_ZIP_ENTRY_NOT_SIGNED, entryName);
                continue;
            }
            if (firstSignedEntrySigners == null) {
                firstSignedEntrySigners = entrySigners;
                firstSignedEntryName = entryName;
            } else if (!entrySigners.equals(firstSignedEntrySigners)) {
                result.addError(
                        Issue.JAR_SIG_ZIP_ENTRY_SIGNERS_MISMATCH,
                        firstSignedEntryName,
                        getSignerNames(firstSignedEntrySigners),
                        entryName,
                        getSignerNames(entrySigners));
                continue;
            }

            Collection<NamedDigest> expectedDigests =
                    getDigestsToVerify(manifestSection, "-Digest", minSdkVersion, maxSdkVersion);
            if (expectedDigests.isEmpty()) {
                result.addError(Issue.JAR_SIG_NO_ZIP_ENTRY_DIGEST_IN_MANIFEST, entryName);
                continue;
            }

            MessageDigest[] mds = new MessageDigest[expectedDigests.size()];
            int mdIndex = 0;
            for (NamedDigest expectedDigest : expectedDigests) {
                mds[mdIndex] = getMessageDigest(expectedDigest.jcaDigestAlgorithm);
                mdIndex++;
            }

            try {
                LocalFileRecord.outputUncompressedData(
                        apk,
                        cdRecord,
                        cdOffsetInApk,
                        new MessageDigestSink(mds));
            } catch (ZipFormatException e) {
                throw new ApkFormatException("Malformed ZIP entry: " + entryName, e);
            } catch (IOException e) {
                throw new IOException("Failed to read entry: " + entryName, e);
            }

            mdIndex = 0;
            for (NamedDigest expectedDigest : expectedDigests) {
                byte[] actualDigest = mds[mdIndex].digest();
                if (!Arrays.equals(expectedDigest.digest, actualDigest)) {
                    result.addError(
                            Issue.JAR_SIG_ZIP_ENTRY_DIGEST_DID_NOT_VERIFY,
                            entryName,
                            expectedDigest.jcaDigestAlgorithm,
                            V1SchemeSigner.MANIFEST_ENTRY_NAME,
                            Base64.getEncoder().encodeToString(actualDigest),
                            Base64.getEncoder().encodeToString(expectedDigest.digest));
                }
            }
        }

        if (firstSignedEntrySigners == null) {
            result.addError(Issue.JAR_SIG_NO_SIGNED_ZIP_ENTRIES);
            return Collections.emptySet();
        } else {
            return new HashSet<>(firstSignedEntrySigners);
        }
    }

    private static List<String> getSignerNames(List<Signer> signers) {
        if (signers.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(signers.size());
        for (Signer signer : signers) {
            result.add(signer.getName());
        }
        return result;
    }

    private static MessageDigest getMessageDigest(String algorithm)
            throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(algorithm);
    }

    private static byte[] digest(String algorithm, byte[] data, int offset, int length)
            throws NoSuchAlgorithmException {
        MessageDigest md = getMessageDigest(algorithm);
        md.update(data, offset, length);
        return md.digest();
    }

    private static byte[] digest(String algorithm, byte[] data) throws NoSuchAlgorithmException {
        return getMessageDigest(algorithm).digest(data);
    }

    private static class NamedDigest {
        private final String jcaDigestAlgorithm;
        private final byte[] digest;

        private NamedDigest(String jcaDigestAlgorithm, byte[] digest) {
            this.jcaDigestAlgorithm = jcaDigestAlgorithm;
            this.digest = digest;
        }
    }

    public static class Result {

        /** Whether the APK's JAR signature verifies. */
        public boolean verified;

        /** List of APK's signers. These signers are used by Android. */
        public final List<SignerInfo> signers = new ArrayList<>();

        /**
         * Signers encountered in the APK but not included in the set of the APK's signers. These
         * signers are ignored by Android.
         */
        public final List<SignerInfo> ignoredSigners = new ArrayList<>();

        private final List<IssueWithParams> mWarnings = new ArrayList<>();
        private final List<IssueWithParams> mErrors = new ArrayList<>();

        private boolean containsErrors() {
            if (!mErrors.isEmpty()) {
                return true;
            }
            for (SignerInfo signer : signers) {
                if (signer.containsErrors()) {
                    return true;
                }
            }
            return false;
        }

        private void addError(Issue msg, Object... parameters) {
            mErrors.add(new IssueWithParams(msg, parameters));
        }

        private void addWarning(Issue msg, Object... parameters) {
            mWarnings.add(new IssueWithParams(msg, parameters));
        }

        public List<IssueWithParams> getErrors() {
            return mErrors;
        }

        public List<IssueWithParams> getWarnings() {
            return mWarnings;
        }

        public static class SignerInfo {
            public final String name;
            public final String signatureFileName;
            public final String signatureBlockFileName;
            public final List<X509Certificate> certChain = new ArrayList<>();

            private final List<IssueWithParams> mWarnings = new ArrayList<>();
            private final List<IssueWithParams> mErrors = new ArrayList<>();

            private SignerInfo(
                    String name, String signatureBlockFileName, String signatureFileName) {
                this.name = name;
                this.signatureBlockFileName = signatureBlockFileName;
                this.signatureFileName = signatureFileName;
            }

            private boolean containsErrors() {
                return !mErrors.isEmpty();
            }

            private void addError(Issue msg, Object... parameters) {
                mErrors.add(new IssueWithParams(msg, parameters));
            }

            private void addWarning(Issue msg, Object... parameters) {
                mWarnings.add(new IssueWithParams(msg, parameters));
            }

            public List<IssueWithParams> getErrors() {
                return mErrors;
            }

            public List<IssueWithParams> getWarnings() {
                return mWarnings;
            }
        }
    }
}
