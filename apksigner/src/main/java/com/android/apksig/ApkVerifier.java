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
import com.android.apksig.internal.apk.v1.V1SchemeVerifier;
import com.android.apksig.internal.apk.v2.ContentDigestAlgorithm;
import com.android.apksig.internal.apk.v2.SignatureAlgorithm;
import com.android.apksig.internal.apk.v2.V2SchemeVerifier;
import com.android.apksig.internal.util.AndroidSdkVersion;
import com.android.apksig.internal.zip.CentralDirectoryRecord;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.apksig.zip.ZipFormatException;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * APK signature verifier which mimics the behavior of the Android platform.
 *
 * <p>The verifier is designed to closely mimic the behavior of Android platforms. This is to enable
 * the verifier to be used for checking whether an APK's signatures will verify on Android.
 *
 * <p>Use {@link Builder} to obtain instances of this verifier.
 *
 * @see <a href="https://source.android.com/security/apksigning/index.html">Application Signing</a>
 */
public class ApkVerifier {

    private static final int APK_SIGNATURE_SCHEME_V2_ID = 2;
    private static final Map<Integer, String> SUPPORTED_APK_SIG_SCHEME_NAMES =
            Collections.singletonMap(APK_SIGNATURE_SCHEME_V2_ID, "APK Signature Scheme v2");

    private final File mApkFile;
    private final DataSource mApkDataSource;

    private final Integer mMinSdkVersion;
    private final int mMaxSdkVersion;

    private ApkVerifier(
            File apkFile,
            DataSource apkDataSource,
            Integer minSdkVersion,
            int maxSdkVersion) {
        mApkFile = apkFile;
        mApkDataSource = apkDataSource;
        mMinSdkVersion = minSdkVersion;
        mMaxSdkVersion = maxSdkVersion;
    }

    /**
     * Verifies the APK's signatures and returns the result of verification. The APK can be
     * considered verified iff the result's {@link Result#isVerified()} returns {@code true}.
     * The verification result also includes errors, warnings, and information about signers.
     *
     * @throws IOException if an I/O error is encountered while reading the APK
     * @throws ApkFormatException if the APK is malformed
     * @throws NoSuchAlgorithmException if the APK's signatures cannot be verified because a
     *         required cryptographic algorithm implementation is missing
     * @throws IllegalStateException if this verifier's configuration is missing required
     *         information.
     */
    public Result verify() throws IOException, ApkFormatException, NoSuchAlgorithmException,
            IllegalStateException {
        Closeable in = null;
        try {
            DataSource apk;
            if (mApkDataSource != null) {
                apk = mApkDataSource;
            } else if (mApkFile != null) {
                RandomAccessFile f = new RandomAccessFile(mApkFile, "r");
                in = f;
                apk = DataSources.asDataSource(f, 0, f.length());
            } else {
                throw new IllegalStateException("APK not provided");
            }
            return verify(apk);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Verifies the APK's signatures and returns the result of verification. The APK can be
     * considered verified iff the result's {@link Result#isVerified()} returns {@code true}.
     * The verification result also includes errors, warnings, and information about signers.
     *
     * @param apk APK file contents
     *
     * @throws IOException if an I/O error is encountered while reading the APK
     * @throws ApkFormatException if the APK is malformed
     * @throws NoSuchAlgorithmException if the APK's signatures cannot be verified because a
     *         required cryptographic algorithm implementation is missing
     */
    private Result verify(DataSource apk)
            throws IOException, ApkFormatException, NoSuchAlgorithmException {
        if (mMinSdkVersion != null) {
            if (mMinSdkVersion < 0) {
                throw new IllegalArgumentException(
                        "minSdkVersion must not be negative: " + mMinSdkVersion);
            }
            if ((mMinSdkVersion != null) && (mMinSdkVersion > mMaxSdkVersion)) {
                throw new IllegalArgumentException(
                        "minSdkVersion (" + mMinSdkVersion + ") > maxSdkVersion (" + mMaxSdkVersion
                                + ")");
            }
        }
        ApkUtils.ZipSections zipSections;
        try {
            zipSections = ApkUtils.findZipSections(apk);
        } catch (ZipFormatException e) {
            throw new ApkFormatException("Malformed APK: not a ZIP archive", e);
        }

        int minSdkVersion;
        if (mMinSdkVersion != null) {
            // No need to obtain minSdkVersion from the APK's AndroidManifest.xml
            minSdkVersion = mMinSdkVersion;
        } else {
            // Need to obtain minSdkVersion from the APK's AndroidManifest.xml
            List<CentralDirectoryRecord> cdRecords;
            try {
                cdRecords = V1SchemeVerifier.parseZipCentralDirectory(apk, zipSections);
            } catch (ApkFormatException e) {
                throw new MinSdkVersionException(
                        "Unable to determine APK's minimum supported Android platform version", e);
            }
            minSdkVersion =
                    ApkSigner.getMinSdkVersionFromApk(
                            cdRecords, apk.slice(0, zipSections.getZipCentralDirectoryOffset()));
            if (minSdkVersion > mMaxSdkVersion) {
                throw new IllegalArgumentException(
                        "minSdkVersion from APK (" + minSdkVersion + ") > maxSdkVersion ("
                                + mMaxSdkVersion + ")");
            }
        }
        int maxSdkVersion = mMaxSdkVersion;

        Result result = new Result();

        // Android N and newer attempts to verify APK Signature Scheme v2 signature of the APK.
        // If the signature is not found, it falls back to JAR signature verification. If the
        // signature is found but does not verify, the APK is rejected.
        Set<Integer> foundApkSigSchemeIds;
        if (maxSdkVersion >= AndroidSdkVersion.N) {
            foundApkSigSchemeIds = new HashSet<>(1);
            try {
                V2SchemeVerifier.Result v2Result = V2SchemeVerifier.verify(apk, zipSections);
                foundApkSigSchemeIds.add(APK_SIGNATURE_SCHEME_V2_ID);
                result.mergeFrom(v2Result);
            } catch (V2SchemeVerifier.SignatureNotFoundException ignored) {}
            if (result.containsErrors()) {
                return result;
            }
        } else {
            foundApkSigSchemeIds = Collections.emptySet();
        }

        // Attempt to verify the APK using JAR signing if necessary. Platforms prior to Android N
        // ignore APK Signature Scheme v2 signatures and always attempt to verify JAR signatures.
        // Android N onwards verifies JAR signatures only if no APK Signature Scheme v2 (or newer
        // scheme) signatures were found.
        if ((minSdkVersion < AndroidSdkVersion.N) || (foundApkSigSchemeIds.isEmpty())) {
            V1SchemeVerifier.Result v1Result =
                    V1SchemeVerifier.verify(
                            apk,
                            zipSections,
                            SUPPORTED_APK_SIG_SCHEME_NAMES,
                            foundApkSigSchemeIds,
                            minSdkVersion,
                            maxSdkVersion);
            result.mergeFrom(v1Result);
        }
        if (result.containsErrors()) {
            return result;
        }

        // Check whether v1 and v2 scheme signer identifies match, provided both v1 and v2
        // signatures verified.
        if ((result.isVerifiedUsingV1Scheme()) && (result.isVerifiedUsingV2Scheme())) {
            ArrayList<Result.V1SchemeSignerInfo> v1Signers =
                    new ArrayList<>(result.getV1SchemeSigners());
            ArrayList<Result.V2SchemeSignerInfo> v2Signers =
                    new ArrayList<>(result.getV2SchemeSigners());
            ArrayList<ByteArray> v1SignerCerts = new ArrayList<>();
            ArrayList<ByteArray> v2SignerCerts = new ArrayList<>();
            for (Result.V1SchemeSignerInfo signer : v1Signers) {
                try {
                    v1SignerCerts.add(new ByteArray(signer.getCertificate().getEncoded()));
                } catch (CertificateEncodingException e) {
                    throw new RuntimeException(
                            "Failed to encode JAR signer " + signer.getName() + " certs", e);
                }
            }
            for (Result.V2SchemeSignerInfo signer : v2Signers) {
                try {
                    v2SignerCerts.add(new ByteArray(signer.getCertificate().getEncoded()));
                } catch (CertificateEncodingException e) {
                    throw new RuntimeException(
                            "Failed to encode APK Signature Scheme v2 signer (index: "
                                    + signer.getIndex() + ") certs",
                            e);
                }
            }

            for (int i = 0; i < v1SignerCerts.size(); i++) {
                ByteArray v1Cert = v1SignerCerts.get(i);
                if (!v2SignerCerts.contains(v1Cert)) {
                    Result.V1SchemeSignerInfo v1Signer = v1Signers.get(i);
                    v1Signer.addError(Issue.V2_SIG_MISSING);
                    break;
                }
            }
            for (int i = 0; i < v2SignerCerts.size(); i++) {
                ByteArray v2Cert = v2SignerCerts.get(i);
                if (!v1SignerCerts.contains(v2Cert)) {
                    Result.V2SchemeSignerInfo v2Signer = v2Signers.get(i);
                    v2Signer.addError(Issue.JAR_SIG_MISSING);
                    break;
                }
            }
        }
        if (result.containsErrors()) {
            return result;
        }

        // Verified
        result.setVerified();
        if (result.isVerifiedUsingV2Scheme()) {
            for (Result.V2SchemeSignerInfo signerInfo : result.getV2SchemeSigners()) {
                result.addSignerCertificate(signerInfo.getCertificate());
            }
        } else if (result.isVerifiedUsingV1Scheme()) {
            for (Result.V1SchemeSignerInfo signerInfo : result.getV1SchemeSigners()) {
                result.addSignerCertificate(signerInfo.getCertificate());
            }
        } else {
            throw new RuntimeException(
                    "APK considered verified, but has not verified using either v1 or v2 schemes");
        }

        return result;
    }

    /**
     * Result of verifying an APKs signatures. The APK can be considered verified iff
     * {@link #isVerified()} returns {@code true}.
     */
    public static class Result {
        private final List<IssueWithParams> mErrors = new ArrayList<>();
        private final List<IssueWithParams> mWarnings = new ArrayList<>();
        private final List<X509Certificate> mSignerCerts = new ArrayList<>();
        private final List<V1SchemeSignerInfo> mV1SchemeSigners = new ArrayList<>();
        private final List<V1SchemeSignerInfo> mV1SchemeIgnoredSigners = new ArrayList<>();
        private final List<V2SchemeSignerInfo> mV2SchemeSigners = new ArrayList<>();

        private boolean mVerified;
        private boolean mVerifiedUsingV1Scheme;
        private boolean mVerifiedUsingV2Scheme;

        /**
         * Returns {@code true} if the APK's signatures verified.
         */
        public boolean isVerified() {
            return mVerified;
        }

        private void setVerified() {
            mVerified = true;
        }

        /**
         * Returns {@code true} if the APK's JAR signatures verified.
         */
        public boolean isVerifiedUsingV1Scheme() {
            return mVerifiedUsingV1Scheme;
        }

        /**
         * Returns {@code true} if the APK's APK Signature Scheme v2 signatures verified.
         */
        public boolean isVerifiedUsingV2Scheme() {
            return mVerifiedUsingV2Scheme;
        }

        /**
         * Returns the verified signers' certificates, one per signer.
         */
        public List<X509Certificate> getSignerCertificates() {
            return mSignerCerts;
        }

        private void addSignerCertificate(X509Certificate cert) {
            mSignerCerts.add(cert);
        }

        /**
         * Returns information about JAR signers associated with the APK's signature. These are the
         * signers used by Android.
         *
         * @see #getV1SchemeIgnoredSigners()
         */
        public List<V1SchemeSignerInfo> getV1SchemeSigners() {
            return mV1SchemeSigners;
        }

        /**
         * Returns information about JAR signers ignored by the APK's signature verification
         * process. These signers are ignored by Android. However, each signer's errors or warnings
         * will contain information about why they are ignored.
         *
         * @see #getV1SchemeSigners()
         */
        public List<V1SchemeSignerInfo> getV1SchemeIgnoredSigners() {
            return mV1SchemeIgnoredSigners;
        }

        /**
         * Returns information about APK Signature Scheme v2 signers associated with the APK's
         * signature.
         */
        public List<V2SchemeSignerInfo> getV2SchemeSigners() {
            return mV2SchemeSigners;
        }

        /**
         * Returns errors encountered while verifying the APK's signatures.
         */
        public List<IssueWithParams> getErrors() {
            return mErrors;
        }

        /**
         * Returns warnings encountered while verifying the APK's signatures.
         */
        public List<IssueWithParams> getWarnings() {
            return mWarnings;
        }

        private void mergeFrom(V1SchemeVerifier.Result source) {
            mVerifiedUsingV1Scheme = source.verified;
            mErrors.addAll(source.getErrors());
            mWarnings.addAll(source.getWarnings());
            for (V1SchemeVerifier.Result.SignerInfo signer : source.signers) {
                mV1SchemeSigners.add(new V1SchemeSignerInfo(signer));
            }
            for (V1SchemeVerifier.Result.SignerInfo signer : source.ignoredSigners) {
                mV1SchemeIgnoredSigners.add(new V1SchemeSignerInfo(signer));
            }
        }

        private void mergeFrom(V2SchemeVerifier.Result source) {
            mVerifiedUsingV2Scheme = source.verified;
            mErrors.addAll(source.getErrors());
            mWarnings.addAll(source.getWarnings());
            for (V2SchemeVerifier.Result.SignerInfo signer : source.signers) {
                mV2SchemeSigners.add(new V2SchemeSignerInfo(signer));
            }
        }

        /**
         * Returns {@code true} if an error was encountered while verifying the APK. Any error
         * prevents the APK from being considered verified.
         */
        public boolean containsErrors() {
            if (!mErrors.isEmpty()) {
                return true;
            }
            if (!mV1SchemeSigners.isEmpty()) {
                for (V1SchemeSignerInfo signer : mV1SchemeSigners) {
                    if (signer.containsErrors()) {
                        return true;
                    }
                }
            }
            if (!mV2SchemeSigners.isEmpty()) {
                for (V2SchemeSignerInfo signer : mV2SchemeSigners) {
                    if (signer.containsErrors()) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * Information about a JAR signer associated with the APK's signature.
         */
        public static class V1SchemeSignerInfo {
            private final String mName;
            private final List<X509Certificate> mCertChain;
            private final String mSignatureBlockFileName;
            private final String mSignatureFileName;

            private final List<IssueWithParams> mErrors;
            private final List<IssueWithParams> mWarnings;

            private V1SchemeSignerInfo(V1SchemeVerifier.Result.SignerInfo result) {
                mName = result.name;
                mCertChain = result.certChain;
                mSignatureBlockFileName = result.signatureBlockFileName;
                mSignatureFileName = result.signatureFileName;
                mErrors = result.getErrors();
                mWarnings = result.getWarnings();
            }

            /**
             * Returns a user-friendly name of the signer.
             */
            public String getName() {
                return mName;
            }

            /**
             * Returns the name of the JAR entry containing this signer's JAR signature block file.
             */
            public String getSignatureBlockFileName() {
                return mSignatureBlockFileName;
            }

            /**
             * Returns the name of the JAR entry containing this signer's JAR signature file.
             */
            public String getSignatureFileName() {
                return mSignatureFileName;
            }

            /**
             * Returns this signer's signing certificate or {@code null} if not available. The
             * certificate is guaranteed to be available if no errors were encountered during
             * verification (see {@link #containsErrors()}.
             *
             * <p>This certificate contains the signer's public key.
             */
            public X509Certificate getCertificate() {
                return mCertChain.isEmpty() ? null : mCertChain.get(0);
            }

            /**
             * Returns the certificate chain for the signer's public key. The certificate containing
             * the public key is first, followed by the certificate (if any) which issued the
             * signing certificate, and so forth. An empty list may be returned if an error was
             * encountered during verification (see {@link #containsErrors()}).
             */
            public List<X509Certificate> getCertificateChain() {
                return mCertChain;
            }

            /**
             * Returns {@code true} if an error was encountered while verifying this signer's JAR
             * signature. Any error prevents the signer's signature from being considered verified.
             */
            public boolean containsErrors() {
                return !mErrors.isEmpty();
            }

            /**
             * Returns errors encountered while verifying this signer's JAR signature. Any error
             * prevents the signer's signature from being considered verified.
             */
            public List<IssueWithParams> getErrors() {
                return mErrors;
            }

            /**
             * Returns warnings encountered while verifying this signer's JAR signature. Warnings
             * do not prevent the signer's signature from being considered verified.
             */
            public List<IssueWithParams> getWarnings() {
                return mWarnings;
            }

            private void addError(Issue msg, Object... parameters) {
                mErrors.add(new IssueWithParams(msg, parameters));
            }
        }

        /**
         * Information about an APK Signature Scheme v2 signer associated with the APK's signature.
         */
        public static class V2SchemeSignerInfo {
            private final int mIndex;
            private final List<X509Certificate> mCerts;

            private final List<IssueWithParams> mErrors;
            private final List<IssueWithParams> mWarnings;

            private V2SchemeSignerInfo(V2SchemeVerifier.Result.SignerInfo result) {
                mIndex = result.index;
                mCerts = result.certs;
                mErrors = result.getErrors();
                mWarnings = result.getWarnings();
            }

            /**
             * Returns this signer's {@code 0}-based index in the list of signers contained in the
             * APK's APK Signature Scheme v2 signature.
             */
            public int getIndex() {
                return mIndex;
            }

            /**
             * Returns this signer's signing certificate or {@code null} if not available. The
             * certificate is guaranteed to be available if no errors were encountered during
             * verification (see {@link #containsErrors()}.
             *
             * <p>This certificate contains the signer's public key.
             */
            public X509Certificate getCertificate() {
                return mCerts.isEmpty() ? null : mCerts.get(0);
            }

            /**
             * Returns this signer's certificates. The first certificate is for the signer's public
             * key. An empty list may be returned if an error was encountered during verification
             * (see {@link #containsErrors()}).
             */
            public List<X509Certificate> getCertificates() {
                return mCerts;
            }

            private void addError(Issue msg, Object... parameters) {
                mErrors.add(new IssueWithParams(msg, parameters));
            }

            public boolean containsErrors() {
                return !mErrors.isEmpty();
            }

            public List<IssueWithParams> getErrors() {
                return mErrors;
            }

            public List<IssueWithParams> getWarnings() {
                return mWarnings;
            }
        }
    }

    /**
     * Error or warning encountered while verifying an APK's signatures.
     */
    public static enum Issue {

        /**
         * APK is not JAR-signed.
         */
        JAR_SIG_NO_SIGNATURES("No JAR signatures"),

        /**
         * APK does not contain any entries covered by JAR signatures.
         */
        JAR_SIG_NO_SIGNED_ZIP_ENTRIES("No JAR entries covered by JAR signatures"),

        /**
         * APK contains multiple entries with the same name.
         *
         * <ul>
         * <li>Parameter 1: name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_DUPLICATE_ZIP_ENTRY("Duplicate entry: %1$s"),

        /**
         * JAR manifest contains a section with a duplicate name.
         *
         * <ul>
         * <li>Parameter 1: section name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_DUPLICATE_MANIFEST_SECTION("Duplicate section in META-INF/MANIFEST.MF: %1$s"),

        /**
         * JAR manifest contains a section without a name.
         *
         * <ul>
         * <li>Parameter 1: section index (1-based) ({@code Integer})</li>
         * </ul>
         */
        JAR_SIG_UNNNAMED_MANIFEST_SECTION(
                "Malformed META-INF/MANIFEST.MF: invidual section #%1$d does not have a name"),

        /**
         * JAR signature file contains a section without a name.
         *
         * <ul>
         * <li>Parameter 1: signature file name ({@code String})</li>
         * <li>Parameter 2: section index (1-based) ({@code Integer})</li>
         * </ul>
         */
        JAR_SIG_UNNNAMED_SIG_FILE_SECTION(
                "Malformed %1$s: invidual section #%2$d does not have a name"),

        /** APK is missing the JAR manifest entry (META-INF/MANIFEST.MF). */
        JAR_SIG_NO_MANIFEST("Missing META-INF/MANIFEST.MF"),

        /**
         * JAR manifest references an entry which is not there in the APK.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_MISSING_ZIP_ENTRY_REFERENCED_IN_MANIFEST(
                "%1$s entry referenced by META-INF/MANIFEST.MF not found in the APK"),

        /**
         * JAR manifest does not list a digest for the specified entry.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_NO_ZIP_ENTRY_DIGEST_IN_MANIFEST("No digest for %1$s in META-INF/MANIFEST.MF"),

        /**
         * JAR signature does not list a digest for the specified entry.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * <li>Parameter 2: signature file name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_NO_ZIP_ENTRY_DIGEST_IN_SIG_FILE("No digest for %1$s in %2$s"),

        /**
         * The specified JAR entry is not covered by JAR signature.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_ZIP_ENTRY_NOT_SIGNED("%1$s entry not signed"),

        /**
         * JAR signature uses different set of signers to protect the two specified ZIP entries.
         *
         * <ul>
         * <li>Parameter 1: first entry name ({@code String})</li>
         * <li>Parameter 2: first entry signer names ({@code List<String>})</li>
         * <li>Parameter 3: second entry name ({@code String})</li>
         * <li>Parameter 4: second entry signer names ({@code List<String>})</li>
         * </ul>
         */
        JAR_SIG_ZIP_ENTRY_SIGNERS_MISMATCH(
                "Entries %1$s and %3$s are signed with different sets of signers"
                        + " : <%2$s> vs <%4$s>"),

        /**
         * Digest of the specified ZIP entry's data does not match the digest expected by the JAR
         * signature.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * <li>Parameter 2: digest algorithm (e.g., SHA-256) ({@code String})</li>
         * <li>Parameter 3: name of the entry in which the expected digest is specified
         *     ({@code String})</li>
         * <li>Parameter 4: base64-encoded actual digest ({@code String})</li>
         * <li>Parameter 5: base64-encoded expected digest ({@code String})</li>
         * </ul>
         */
        JAR_SIG_ZIP_ENTRY_DIGEST_DID_NOT_VERIFY(
                "%2$s digest of %1$s does not match the digest specified in %3$s"
                        + ". Expected: <%5$s>, actual: <%4$s>"),

        /**
         * Digest of the JAR manifest main section did not verify.
         *
         * <ul>
         * <li>Parameter 1: digest algorithm (e.g., SHA-256) ({@code String})</li>
         * <li>Parameter 2: name of the entry in which the expected digest is specified
         *     ({@code String})</li>
         * <li>Parameter 3: base64-encoded actual digest ({@code String})</li>
         * <li>Parameter 4: base64-encoded expected digest ({@code String})</li>
         * </ul>
         */
        JAR_SIG_MANIFEST_MAIN_SECTION_DIGEST_DID_NOT_VERIFY(
                "%1$s digest of META-INF/MANIFEST.MF main section does not match the digest"
                        + " specified in %2$s. Expected: <%4$s>, actual: <%3$s>"),

        /**
         * Digest of the specified JAR manifest section does not match the digest expected by the
         * JAR signature.
         *
         * <ul>
         * <li>Parameter 1: section name ({@code String})</li>
         * <li>Parameter 2: digest algorithm (e.g., SHA-256) ({@code String})</li>
         * <li>Parameter 3: name of the signature file in which the expected digest is specified
         *     ({@code String})</li>
         * <li>Parameter 4: base64-encoded actual digest ({@code String})</li>
         * <li>Parameter 5: base64-encoded expected digest ({@code String})</li>
         * </ul>
         */
        JAR_SIG_MANIFEST_SECTION_DIGEST_DID_NOT_VERIFY(
                "%2$s digest of META-INF/MANIFEST.MF section for %1$s does not match the digest"
                        + " specified in %3$s. Expected: <%5$s>, actual: <%4$s>"),

        /**
         * JAR signature file does not contain the whole-file digest of the JAR manifest file. The
         * digest speeds up verification of JAR signature.
         *
         * <ul>
         * <li>Parameter 1: name of the signature file ({@code String})</li>
         * </ul>
         */
        JAR_SIG_NO_MANIFEST_DIGEST_IN_SIG_FILE(
                "%1$s does not specify digest of META-INF/MANIFEST.MF"
                        + ". This slows down verification."),

        /**
         * APK is signed using APK Signature Scheme v2 or newer, but JAR signature file does not
         * contain protections against stripping of these newer scheme signatures.
         *
         * <ul>
         * <li>Parameter 1: name of the signature file ({@code String})</li>
         * </ul>
         */
        JAR_SIG_NO_APK_SIG_STRIP_PROTECTION(
                "APK is signed using APK Signature Scheme v2 but these signatures may be stripped"
                        + " without being detected because %1$s does not contain anti-stripping"
                        + " protections."),

        /**
         * JAR signature of the signer is missing a file/entry.
         *
         * <ul>
         * <li>Parameter 1: name of the encountered file ({@code String})</li>
         * <li>Parameter 2: name of the missing file ({@code String})</li>
         * </ul>
         */
        JAR_SIG_MISSING_FILE("Partial JAR signature. Found: %1$s, missing: %2$s"),

        /**
         * An exception was encountered while verifying JAR signature contained in a signature block
         * against the signature file.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * <li>Parameter 2: name of the signature file ({@code String})</li>
         * <li>Parameter 3: exception ({@code Throwable})</li>
         * </ul>
         */
        JAR_SIG_VERIFY_EXCEPTION("Failed to verify JAR signature %1$s against %2$s: %3$s"),

        /**
         * JAR signature contains unsupported digest algorithm.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * <li>Parameter 2: digest algorithm OID ({@code String})</li>
         * <li>Parameter 2: signature algorithm OID ({@code String})</li>
         * <li>Parameter 3: API Levels on which this combination of algorithms is not supported
         *     ({@code String})</li>
         * </ul>
         */
        JAR_SIG_UNSUPPORTED_SIG_ALG(
                "JAR signature %1$s uses digest algorithm %2$s and signature algorithm %3$s which"
                        + " is not supported on API Levels %4$s"),

        /**
         * An exception was encountered while parsing JAR signature contained in a signature block.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * <li>Parameter 2: exception ({@code Throwable})</li>
         * </ul>
         */
        JAR_SIG_PARSE_EXCEPTION("Failed to parse JAR signature %1$s: %2$s"),

        /**
         * An exception was encountered while parsing a certificate contained in the JAR signature
         * block.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * <li>Parameter 2: exception ({@code Throwable})</li>
         * </ul>
         */
        JAR_SIG_MALFORMED_CERTIFICATE("Malformed certificate in JAR signature %1$s: %2$s"),

        /**
         * JAR signature contained in a signature block file did not verify against the signature
         * file.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * <li>Parameter 2: name of the signature file ({@code String})</li>
         * </ul>
         */
        JAR_SIG_DID_NOT_VERIFY("JAR signature %1$s did not verify against %2$s"),

        /**
         * JAR signature contains no verified signers.
         *
         * <ul>
         * <li>Parameter 1: name of the signature block file ({@code String})</li>
         * </ul>
         */
        JAR_SIG_NO_SIGNERS("JAR signature %1$s contains no signers"),

        /**
         * JAR signature file contains a section with a duplicate name.
         *
         * <ul>
         * <li>Parameter 1: signature file name ({@code String})</li>
         * <li>Parameter 1: section name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_DUPLICATE_SIG_FILE_SECTION("Duplicate section in %1$s: %2$s"),

        /**
         * JAR signature file's main section doesn't contain the mandatory Signature-Version
         * attribute.
         *
         * <ul>
         * <li>Parameter 1: signature file name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_MISSING_VERSION_ATTR_IN_SIG_FILE(
                "Malformed %1$s: missing Signature-Version attribute"),

        /**
         * JAR signature file references an unknown APK signature scheme ID.
         *
         * <ul>
         * <li>Parameter 1: name of the signature file ({@code String})</li>
         * <li>Parameter 2: unknown APK signature scheme ID ({@code} Integer)</li>
         * </ul>
         */
        JAR_SIG_UNKNOWN_APK_SIG_SCHEME_ID(
                "JAR signature %1$s references unknown APK signature scheme ID: %2$d"),

        /**
         * JAR signature file indicates that the APK is supposed to be signed with a supported APK
         * signature scheme (in addition to the JAR signature) but no such signature was found in
         * the APK.
         *
         * <ul>
         * <li>Parameter 1: name of the signature file ({@code String})</li>
         * <li>Parameter 2: APK signature scheme ID ({@code} Integer)</li>
         * <li>Parameter 3: APK signature scheme English name ({@code} String)</li>
         * </ul>
         */
        JAR_SIG_MISSING_APK_SIG_REFERENCED(
                "JAR signature %1$s indicates the APK is signed using %3$s but no such signature"
                        + " was found. Signature stripped?"),

        /**
         * JAR entry is not covered by signature and thus unauthorized modifications to its contents
         * will not be detected.
         *
         * <ul>
         * <li>Parameter 1: entry name ({@code String})</li>
         * </ul>
         */
        JAR_SIG_UNPROTECTED_ZIP_ENTRY(
                "%1$s not protected by signature. Unauthorized modifications to this JAR entry"
                        + " will not be detected. Delete or move the entry outside of META-INF/."),

        /**
         * APK which is both JAR-signed and signed using APK Signature Scheme v2 contains an APK
         * Signature Scheme v2 signature from this signer, but does not contain a JAR signature
         * from this signer.
         */
        JAR_SIG_MISSING("No JAR signature from this signer"),

        /**
         * APK which is both JAR-signed and signed using APK Signature Scheme v2 contains a JAR
         * signature from this signer, but does not contain an APK Signature Scheme v2 signature
         * from this signer.
         */
        V2_SIG_MISSING("No APK Signature Scheme v2 signature from this signer"),

        /**
         * Failed to parse the list of signers contained in the APK Signature Scheme v2 signature.
         */
        V2_SIG_MALFORMED_SIGNERS("Malformed list of signers"),

        /**
         * Failed to parse this signer's signer block contained in the APK Signature Scheme v2
         * signature.
         */
        V2_SIG_MALFORMED_SIGNER("Malformed signer block"),

        /**
         * Public key embedded in the APK Signature Scheme v2 signature of this signer could not be
         * parsed.
         *
         * <ul>
         * <li>Parameter 1: error details ({@code Throwable})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_PUBLIC_KEY("Malformed public key: %1$s"),

        /**
         * This APK Signature Scheme v2 signer's certificate could not be parsed.
         *
         * <ul>
         * <li>Parameter 1: index ({@code 0}-based) of the certificate in the signer's list of
         *     certificates ({@code Integer})</li>
         * <li>Parameter 2: sequence number ({@code 1}-based) of the certificate in the signer's
         *     list of certificates ({@code Integer})</li>
         * <li>Parameter 3: error details ({@code Throwable})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_CERTIFICATE("Malformed certificate #%2$d: %3$s"),

        /**
         * Failed to parse this signer's signature record contained in the APK Signature Scheme v2
         * signature.
         *
         * <ul>
         * <li>Parameter 1: record number (first record is {@code 1}) ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_SIGNATURE("Malformed APK Signature Scheme v2 signature record #%1$d"),

        /**
         * Failed to parse this signer's digest record contained in the APK Signature Scheme v2
         * signature.
         *
         * <ul>
         * <li>Parameter 1: record number (first record is {@code 1}) ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_DIGEST("Malformed APK Signature Scheme v2 digest record #%1$d"),

        /**
         * This APK Signature Scheme v2 signer contains a malformed additional attribute.
         *
         * <ul>
         * <li>Parameter 1: attribute number (first attribute is {@code 1}) {@code Integer})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_ADDITIONAL_ATTRIBUTE("Malformed additional attribute #%1$d"),

        /**
         * APK Signature Scheme v2 signature contains no signers.
         */
        V2_SIG_NO_SIGNERS("No signers in APK Signature Scheme v2 signature"),

        /**
         * This APK Signature Scheme v2 signer contains a signature produced using an unknown
         * algorithm.
         *
         * <ul>
         * <li>Parameter 1: algorithm ID ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_UNKNOWN_SIG_ALGORITHM("Unknown signature algorithm: %1$#x"),

        /**
         * This APK Signature Scheme v2 signer contains an unknown additional attribute.
         *
         * <ul>
         * <li>Parameter 1: attribute ID ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_UNKNOWN_ADDITIONAL_ATTRIBUTE("Unknown additional attribute: ID %1$#x"),

        /**
         * An exception was encountered while verifying APK Signature Scheme v2 signature of this
         * signer.
         *
         * <ul>
         * <li>Parameter 1: signature algorithm ({@link SignatureAlgorithm})</li>
         * <li>Parameter 2: exception ({@code Throwable})</li>
         * </ul>
         */
        V2_SIG_VERIFY_EXCEPTION("Failed to verify %1$s signature: %2$s"),

        /**
         * APK Signature Scheme v2 signature over this signer's signed-data block did not verify.
         *
         * <ul>
         * <li>Parameter 1: signature algorithm ({@link SignatureAlgorithm})</li>
         * </ul>
         */
        V2_SIG_DID_NOT_VERIFY("%1$s signature over signed-data did not verify"),

        /**
         * This APK Signature Scheme v2 signer offers no signatures.
         */
        V2_SIG_NO_SIGNATURES("No signatures"),

        /**
         * This APK Signature Scheme v2 signer offers signatures but none of them are supported.
         */
        V2_SIG_NO_SUPPORTED_SIGNATURES("No supported signatures"),

        /**
         * This APK Signature Scheme v2 signer offers no certificates.
         */
        V2_SIG_NO_CERTIFICATES("No certificates"),

        /**
         * This APK Signature Scheme v2 signer's public key listed in the signer's certificate does
         * not match the public key listed in the signatures record.
         *
         * <ul>
         * <li>Parameter 1: hex-encoded public key from certificate ({@code String})</li>
         * <li>Parameter 2: hex-encoded public key from signatures record ({@code String})</li>
         * </ul>
         */
        V2_SIG_PUBLIC_KEY_MISMATCH_BETWEEN_CERTIFICATE_AND_SIGNATURES_RECORD(
                "Public key mismatch between certificate and signature record: <%1$s> vs <%2$s>"),

        /**
         * This APK Signature Scheme v2 signer's signature algorithms listed in the signatures
         * record do not match the signature algorithms listed in the signatures record.
         *
         * <ul>
         * <li>Parameter 1: signature algorithms from signatures record ({@code List<Integer>})</li>
         * <li>Parameter 2: signature algorithms from digests record ({@code List<Integer>})</li>
         * </ul>
         */
        V2_SIG_SIG_ALG_MISMATCH_BETWEEN_SIGNATURES_AND_DIGESTS_RECORDS(
                "Signature algorithms mismatch between signatures and digests records"
                        + ": %1$s vs %2$s"),

        /**
         * The APK's digest does not match the digest contained in the APK Signature Scheme v2
         * signature.
         *
         * <ul>
         * <li>Parameter 1: content digest algorithm ({@link ContentDigestAlgorithm})</li>
         * <li>Parameter 2: hex-encoded expected digest of the APK ({@code String})</li>
         * <li>Parameter 3: hex-encoded actual digest of the APK ({@code String})</li>
         * </ul>
         */
        V2_SIG_APK_DIGEST_DID_NOT_VERIFY(
                "APK integrity check failed. %1$s digest mismatch."
                        + " Expected: <%2$s>, actual: <%3$s>"),

        /**
         * APK Signing Block contains an unknown entry.
         *
         * <ul>
         * <li>Parameter 1: entry ID ({@code Integer})</li>
         * </ul>
         */
        APK_SIG_BLOCK_UNKNOWN_ENTRY_ID("APK Signing Block contains unknown entry: ID %1$#x");

        private final String mFormat;

        private Issue(String format) {
            mFormat = format;
        }

        /**
         * Returns the format string suitable for combining the parameters of this issue into a
         * readable string. See {@link java.util.Formatter} for format.
         */
        private String getFormat() {
            return mFormat;
        }
    }

    /**
     * {@link Issue} with associated parameters. {@link #toString()} produces a readable formatted
     * form.
     */
    public static class IssueWithParams {
        private final Issue mIssue;
        private final Object[] mParams;

        /**
         * Constructs a new {@code IssueWithParams} of the specified type and with provided
         * parameters.
         */
        public IssueWithParams(Issue issue, Object[] params) {
            mIssue = issue;
            mParams = params;
        }

        /**
         * Returns the type of this issue.
         */
        public Issue getIssue() {
            return mIssue;
        }

        /**
         * Returns the parameters of this issue.
         */
        public Object[] getParams() {
            return mParams.clone();
        }

        /**
         * Returns a readable form of this issue.
         */
        @Override
        public String toString() {
            return String.format(mIssue.getFormat(), mParams);
        }
    }

    /**
     * Wrapped around {@code byte[]} which ensures that {@code equals} and {@code hashCode} operate
     * on the contents of the arrays rather than on references.
     */
    private static class ByteArray {
        private final byte[] mArray;
        private final int mHashCode;

        private ByteArray(byte[] arr) {
            mArray = arr;
            mHashCode = Arrays.hashCode(mArray);
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ByteArray other = (ByteArray) obj;
            if (hashCode() != other.hashCode()) {
                return false;
            }
            if (!Arrays.equals(mArray, other.mArray)) {
                return false;
            }
            return true;
        }
    }

    /**
     * Builder of {@link ApkVerifier} instances.
     *
     * <p>The resulting verifier by default checks whether the APK will verify on all platform
     * versions supported by the APK, as specified by {@code android:minSdkVersion} attributes in
     * the APK's {@code AndroidManifest.xml}. The range of platform versions can be customized using
     * {@link #setMinCheckedPlatformVersion(int)} and {@link #setMaxCheckedPlatformVersion(int)}.
     */
    public static class Builder {
        private final File mApkFile;
        private final DataSource mApkDataSource;

        private Integer mMinSdkVersion;
        private int mMaxSdkVersion = Integer.MAX_VALUE;

        /**
         * Constructs a new {@code Builder} for verifying the provided APK file.
         */
        public Builder(File apk) {
            if (apk == null) {
                throw new NullPointerException("apk == null");
            }
            mApkFile = apk;
            mApkDataSource = null;
        }

        /**
         * Constructs a new {@code Builder} for verifying the provided APK.
         */
        public Builder(DataSource apk) {
            if (apk == null) {
                throw new NullPointerException("apk == null");
            }
            mApkDataSource = apk;
            mApkFile = null;
        }

        /**
         * Sets the oldest Android platform version for which the APK is verified. APK verification
         * will confirm that the APK is expected to install successfully on all known Android
         * platforms starting from the platform version with the provided API Level. The upper end
         * of the platform versions range can be modified via
         * {@link #setMaxCheckedPlatformVersion(int)}.
         *
         * <p>This method is useful for overriding the default behavior which checks that the APK
         * will verify on all platform versions supported by the APK, as specified by
         * {@code android:minSdkVersion} attributes in the APK's {@code AndroidManifest.xml}.
         *
         * @param minSdkVersion API Level of the oldest platform for which to verify the APK
         *
         * @see #setMinCheckedPlatformVersion(int)
         */
        public Builder setMinCheckedPlatformVersion(int minSdkVersion) {
            mMinSdkVersion = minSdkVersion;
            return this;
        }

        /**
         * Sets the newest Android platform version for which the APK is verified. APK verification
         * will confirm that the APK is expected to install successfully on all platform versions
         * supported by the APK up until and including the provided version. The lower end
         * of the platform versions range can be modified via
         * {@link #setMinCheckedPlatformVersion(int)}.
         *
         * @param maxSdkVersion API Level of the newest platform for which to verify the APK
         *
         * @see #setMinCheckedPlatformVersion(int)
         */
        public Builder setMaxCheckedPlatformVersion(int maxSdkVersion) {
            mMaxSdkVersion = maxSdkVersion;
            return this;
        }

        /**
         * Returns an {@link ApkVerifier} initialized according to the configuration of this
         * builder.
         */
        public ApkVerifier build() {
            return new ApkVerifier(
                    mApkFile,
                    mApkDataSource,
                    mMinSdkVersion,
                    mMaxSdkVersion);
        }
    }
}
