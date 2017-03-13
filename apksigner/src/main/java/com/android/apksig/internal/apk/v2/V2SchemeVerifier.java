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

package com.android.apksig.internal.apk.v2;

import com.android.apksig.ApkVerifier.Issue;
import com.android.apksig.ApkVerifier.IssueWithParams;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.internal.util.ByteBufferDataSource;
import com.android.apksig.internal.util.DelegatingX509Certificate;
import com.android.apksig.internal.util.Pair;
import com.android.apksig.internal.zip.ZipUtils;
import com.android.apksig.util.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.DigestException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * APK Signature Scheme v2 verifier.
 *
 * <p>APK Signature Scheme v2 is a whole-file signature scheme which aims to protect every single
 * bit of the APK, as opposed to the JAR Signature Scheme which protects only the names and
 * uncompressed contents of ZIP entries.
 *
 * @see <a href="https://source.android.com/security/apksigning/v2.html">APK Signature Scheme v2</a>
 */
public abstract class V2SchemeVerifier {

    private static final long APK_SIG_BLOCK_MAGIC_HI = 0x3234206b636f6c42L;
    private static final long APK_SIG_BLOCK_MAGIC_LO = 0x20676953204b5041L;
    private static final int APK_SIG_BLOCK_MIN_SIZE = 32;

    private static final int APK_SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a;

    /** Hidden constructor to prevent instantiation. */
    private V2SchemeVerifier() {}

    /**
     * Verifies the provided APK's APK Signature Scheme v2 signatures and returns the result of
     * verification. APK is considered verified only if {@link Result#verified} is {@code true}. If
     * verification fails, the result will contain errors -- see {@link Result#getErrors()}.
     *
     * @throws ApkFormatException if the APK is malformed
     * @throws NoSuchAlgorithmException if the APK's signatures cannot be verified because a
     *         required cryptographic algorithm implementation is missing
     * @throws SignatureNotFoundException if no APK Signature Scheme v2 signatures are found
     * @throws IOException if an I/O error occurs when reading the APK
     */
    public static Result verify(DataSource apk, ApkUtils.ZipSections zipSections)
            throws IOException, ApkFormatException, NoSuchAlgorithmException,
                    SignatureNotFoundException {
        Result result = new Result();
        SignatureInfo signatureInfo = findSignature(apk, zipSections, result);

        DataSource beforeApkSigningBlock = apk.slice(0, signatureInfo.apkSigningBlockOffset);
        DataSource centralDir =
                apk.slice(
                        signatureInfo.centralDirOffset,
                        signatureInfo.eocdOffset - signatureInfo.centralDirOffset);
        ByteBuffer eocd = signatureInfo.eocd;

        verify(beforeApkSigningBlock,
                signatureInfo.signatureBlock,
                centralDir,
                eocd,
                result);
        return result;
    }

    /**
     * Verifies the provided APK's v2 signatures and outputs the results into the provided
     * {@code result}. APK is considered verified only if there are no errors reported in the
     * {@code result}.
     */
    private static void verify(
            DataSource beforeApkSigningBlock,
            ByteBuffer apkSignatureSchemeV2Block,
            DataSource centralDir,
            ByteBuffer eocd,
            Result result) throws IOException, NoSuchAlgorithmException {
        Set<ContentDigestAlgorithm> contentDigestsToVerify = new HashSet<>(1);
        parseSigners(apkSignatureSchemeV2Block, contentDigestsToVerify, result);
        if (result.containsErrors()) {
            return;
        }
        verifyIntegrity(
                beforeApkSigningBlock, centralDir, eocd, contentDigestsToVerify, result);
        if (!result.containsErrors()) {
            result.verified = true;
        }
    }

    /**
     * Parses each signer in the provided APK Signature Scheme v2 block and populates
     * {@code signerInfos} of the provided {@code result}.
     *
     * <p>This verifies signatures over {@code signed-data} block contained in each signer block.
     * However, this does not verify the integrity of the rest of the APK but rather simply reports
     * the expected digests of the rest of the APK (see {@code contentDigestsToVerify}).
     */
    private static void parseSigners(
            ByteBuffer apkSignatureSchemeV2Block,
            Set<ContentDigestAlgorithm> contentDigestsToVerify,
            Result result) throws NoSuchAlgorithmException {
        ByteBuffer signers;
        try {
            signers = getLengthPrefixedSlice(apkSignatureSchemeV2Block);
        } catch (ApkFormatException e) {
            result.addError(Issue.V2_SIG_MALFORMED_SIGNERS);
            return;
        }
        if (!signers.hasRemaining()) {
            result.addError(Issue.V2_SIG_NO_SIGNERS);
            return;
        }

        CertificateFactory certFactory;
        try {
            certFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Failed to obtain X.509 CertificateFactory", e);
        }
        int signerCount = 0;
        while (signers.hasRemaining()) {
            int signerIndex = signerCount;
            signerCount++;
            Result.SignerInfo signerInfo = new Result.SignerInfo();
            signerInfo.index = signerIndex;
            result.signers.add(signerInfo);
            try {
                ByteBuffer signer = getLengthPrefixedSlice(signers);
                parseSigner(signer, certFactory, signerInfo, contentDigestsToVerify);
            } catch (ApkFormatException | BufferUnderflowException e) {
                signerInfo.addError(Issue.V2_SIG_MALFORMED_SIGNER);
                return;
            }
        }
    }

    /**
     * Parses the provided signer block and populates the {@code result}.
     *
     * <p>This verifies signatures over {@code signed-data} contained in this block but does not
     * verify the integrity of the rest of the APK. Rather, this method adds to the
     * {@code contentDigestsToVerify}.
     */
    private static void parseSigner(
            ByteBuffer signerBlock,
            CertificateFactory certFactory,
            Result.SignerInfo result,
            Set<ContentDigestAlgorithm> contentDigestsToVerify)
                    throws ApkFormatException, NoSuchAlgorithmException {
        ByteBuffer signedData = getLengthPrefixedSlice(signerBlock);
        byte[] signedDataBytes = new byte[signedData.remaining()];
        signedData.get(signedDataBytes);
        signedData.flip();
        result.signedData = signedDataBytes;

        ByteBuffer signatures = getLengthPrefixedSlice(signerBlock);
        byte[] publicKeyBytes = readLengthPrefixedByteArray(signerBlock);

        // Parse the signatures block and identify supported signatures
        int signatureCount = 0;
        List<SupportedSignature> supportedSignatures = new ArrayList<>(1);
        while (signatures.hasRemaining()) {
            signatureCount++;
            try {
                ByteBuffer signature = getLengthPrefixedSlice(signatures);
                int sigAlgorithmId = signature.getInt();
                byte[] sigBytes = readLengthPrefixedByteArray(signature);
                result.signatures.add(
                        new Result.SignerInfo.Signature(sigAlgorithmId, sigBytes));
                SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.findById(sigAlgorithmId);
                if (signatureAlgorithm == null) {
                    result.addWarning(Issue.V2_SIG_UNKNOWN_SIG_ALGORITHM, sigAlgorithmId);
                    continue;
                }
                supportedSignatures.add(new SupportedSignature(signatureAlgorithm, sigBytes));
            } catch (ApkFormatException | BufferUnderflowException e) {
                result.addError(Issue.V2_SIG_MALFORMED_SIGNATURE, signatureCount);
                return;
            }
        }
        if (result.signatures.isEmpty()) {
            result.addError(Issue.V2_SIG_NO_SIGNATURES);
            return;
        }

        // Verify signatures over signed-data block using the public key
        List<SupportedSignature> signaturesToVerify = getSignaturesToVerify(supportedSignatures);
        if (signaturesToVerify.isEmpty()) {
            result.addError(Issue.V2_SIG_NO_SUPPORTED_SIGNATURES);
            return;
        }
        for (SupportedSignature signature : signaturesToVerify) {
            SignatureAlgorithm signatureAlgorithm = signature.algorithm;
            String jcaSignatureAlgorithm =
                    signatureAlgorithm.getJcaSignatureAlgorithmAndParams().getFirst();
            AlgorithmParameterSpec jcaSignatureAlgorithmParams =
                    signatureAlgorithm.getJcaSignatureAlgorithmAndParams().getSecond();
            String keyAlgorithm = signatureAlgorithm.getJcaKeyAlgorithm();
            PublicKey publicKey;
            try {
                publicKey =
                        KeyFactory.getInstance(keyAlgorithm).generatePublic(
                                new X509EncodedKeySpec(publicKeyBytes));
            } catch (Exception e) {
                result.addError(Issue.V2_SIG_MALFORMED_PUBLIC_KEY, e);
                return;
            }
            try {
                Signature sig = Signature.getInstance(jcaSignatureAlgorithm);
                sig.initVerify(publicKey);
                if (jcaSignatureAlgorithmParams != null) {
                    sig.setParameter(jcaSignatureAlgorithmParams);
                }
                signedData.position(0);
                sig.update(signedData);
                byte[] sigBytes = signature.signature;
                if (!sig.verify(sigBytes)) {
                    result.addError(Issue.V2_SIG_DID_NOT_VERIFY, signatureAlgorithm);
                    return;
                }
                result.verifiedSignatures.put(signatureAlgorithm, sigBytes);
                contentDigestsToVerify.add(signatureAlgorithm.getContentDigestAlgorithm());
            } catch (InvalidKeyException | InvalidAlgorithmParameterException
                    | SignatureException e) {
                result.addError(Issue.V2_SIG_VERIFY_EXCEPTION, signatureAlgorithm, e);
                return;
            }
        }

        // At least one signature over signedData has verified. We can now parse signed-data.
        signedData.position(0);
        ByteBuffer digests = getLengthPrefixedSlice(signedData);
        ByteBuffer certificates = getLengthPrefixedSlice(signedData);
        ByteBuffer additionalAttributes = getLengthPrefixedSlice(signedData);

        // Parse the certificates block
        int certificateIndex = -1;
        while (certificates.hasRemaining()) {
            certificateIndex++;
            byte[] encodedCert = readLengthPrefixedByteArray(certificates);
            X509Certificate certificate;
            try {
                certificate =
                        (X509Certificate)
                                certFactory.generateCertificate(
                                        new ByteArrayInputStream(encodedCert));
            } catch (CertificateException e) {
                result.addError(
                        Issue.V2_SIG_MALFORMED_CERTIFICATE,
                        certificateIndex,
                        certificateIndex + 1,
                        e);
                return;
            }
            // Wrap the cert so that the result's getEncoded returns exactly the original encoded
            // form. Without this, getEncoded may return a different form from what was stored in
            // the signature. This is becase some X509Certificate(Factory) implementations re-encode
            // certificates.
            certificate = new GuaranteedEncodedFormX509Certificate(certificate, encodedCert);
            result.certs.add(certificate);
        }

        if (result.certs.isEmpty()) {
            result.addError(Issue.V2_SIG_NO_CERTIFICATES);
            return;
        }
        X509Certificate mainCertificate = result.certs.get(0);
        byte[] certificatePublicKeyBytes = mainCertificate.getPublicKey().getEncoded();
        if (!Arrays.equals(publicKeyBytes, certificatePublicKeyBytes)) {
            result.addError(
                    Issue.V2_SIG_PUBLIC_KEY_MISMATCH_BETWEEN_CERTIFICATE_AND_SIGNATURES_RECORD,
                    toHex(certificatePublicKeyBytes),
                    toHex(publicKeyBytes));
            return;
        }

        // Parse the digests block
        int digestCount = 0;
        while (digests.hasRemaining()) {
            digestCount++;
            try {
                ByteBuffer digest = getLengthPrefixedSlice(digests);
                int sigAlgorithmId = digest.getInt();
                byte[] digestBytes = readLengthPrefixedByteArray(digest);
                result.contentDigests.add(
                        new Result.SignerInfo.ContentDigest(sigAlgorithmId, digestBytes));
            } catch (ApkFormatException | BufferUnderflowException e) {
                result.addError(Issue.V2_SIG_MALFORMED_DIGEST, digestCount);
                return;
            }
        }

        List<Integer> sigAlgsFromSignaturesRecord = new ArrayList<>(result.signatures.size());
        for (Result.SignerInfo.Signature signature : result.signatures) {
            sigAlgsFromSignaturesRecord.add(signature.getAlgorithmId());
        }
        List<Integer> sigAlgsFromDigestsRecord = new ArrayList<>(result.contentDigests.size());
        for (Result.SignerInfo.ContentDigest digest : result.contentDigests) {
            sigAlgsFromDigestsRecord.add(digest.getSignatureAlgorithmId());
        }

        if (!sigAlgsFromSignaturesRecord.equals(sigAlgsFromDigestsRecord)) {
            result.addError(
                    Issue.V2_SIG_SIG_ALG_MISMATCH_BETWEEN_SIGNATURES_AND_DIGESTS_RECORDS,
                    sigAlgsFromSignaturesRecord,
                    sigAlgsFromDigestsRecord);
            return;
        }

        // Parse the additional attributes block.
        int additionalAttributeCount = 0;
        while (additionalAttributes.hasRemaining()) {
            additionalAttributeCount++;
            try {
                ByteBuffer attribute = getLengthPrefixedSlice(additionalAttributes);
                int id = attribute.getInt();
                byte[] value = readLengthPrefixedByteArray(attribute);
                result.additionalAttributes.add(
                        new Result.SignerInfo.AdditionalAttribute(id, value));
                result.addWarning(Issue.V2_SIG_UNKNOWN_ADDITIONAL_ATTRIBUTE, id);
            } catch (ApkFormatException | BufferUnderflowException e) {
                result.addError(
                        Issue.V2_SIG_MALFORMED_ADDITIONAL_ATTRIBUTE, additionalAttributeCount);
                return;
            }
        }
    }

    private static List<SupportedSignature> getSignaturesToVerify(
            List<SupportedSignature> signatures) {
        // Pick the signature with the strongest algorithm, to mimic Android's behavior.
        SignatureAlgorithm bestSigAlgorithm = null;
        byte[] bestSigAlgorithmSignatureBytes = null;
        for (SupportedSignature sig : signatures) {
            SignatureAlgorithm sigAlgorithm = sig.algorithm;
            if ((bestSigAlgorithm == null)
                    || (compareSignatureAlgorithm(sigAlgorithm, bestSigAlgorithm) > 0)) {
                bestSigAlgorithm = sigAlgorithm;
                bestSigAlgorithmSignatureBytes = sig.signature;
            }
        }

        if (bestSigAlgorithm == null) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(
                    new SupportedSignature(bestSigAlgorithm, bestSigAlgorithmSignatureBytes));
        }
    }

    private static class SupportedSignature {
        private final SignatureAlgorithm algorithm;
        private final byte[] signature;

        private SupportedSignature(SignatureAlgorithm algorithm, byte[] signature) {
            this.algorithm = algorithm;
            this.signature = signature;
        }
    }

    /**
     * Returns positive number if {@code alg1} is preferred over {@code alg2}, {@code -1} if
     * {@code alg2} is preferred over {@code alg1}, and {@code 0} if there is no preference.
     */
    private static int compareSignatureAlgorithm(SignatureAlgorithm alg1, SignatureAlgorithm alg2) {
        ContentDigestAlgorithm digestAlg1 = alg1.getContentDigestAlgorithm();
        ContentDigestAlgorithm digestAlg2 = alg2.getContentDigestAlgorithm();
        return compareContentDigestAlgorithm(digestAlg1, digestAlg2);
    }

    /**
     * Returns positive number if {@code alg1} is preferred over {@code alg2}, {@code -1} if
     * {@code alg2} is preferred over {@code alg1}, and {@code 0} if there is no preference.
     */
    private static int compareContentDigestAlgorithm(
            ContentDigestAlgorithm alg1,
            ContentDigestAlgorithm alg2) {
        switch (alg1) {
            case CHUNKED_SHA256:
                switch (alg2) {
                    case CHUNKED_SHA256:
                        return 0;
                    case CHUNKED_SHA512:
                        return -1;
                    default:
                        throw new IllegalArgumentException("Unknown alg2: " + alg2);
                }
            case CHUNKED_SHA512:
                switch (alg2) {
                    case CHUNKED_SHA256:
                        return 1;
                    case CHUNKED_SHA512:
                        return 0;
                    default:
                        throw new IllegalArgumentException("Unknown alg2: " + alg2);
                }
            default:
                throw new IllegalArgumentException("Unknown alg1: " + alg1);
        }
    }

    /**
     * Verifies integrity of the APK outside of the APK Signing Block by computing digests of the
     * APK and comparing them against the digests listed in APK Signing Block. The expected digests
     * taken from {@code v2SchemeSignerInfos} of the provided {@code result}.
     */
    private static void verifyIntegrity(
            DataSource beforeApkSigningBlock,
            DataSource centralDir,
            ByteBuffer eocd,
            Set<ContentDigestAlgorithm> contentDigestAlgorithms,
            Result result) throws IOException, NoSuchAlgorithmException {
        if (contentDigestAlgorithms.isEmpty()) {
            // This should never occur because this method is invoked once at least one signature
            // is verified, meaning at least one content digest is known.
            throw new RuntimeException("No content digests found");
        }

        // For the purposes of verifying integrity, ZIP End of Central Directory (EoCD) must be
        // treated as though its Central Directory offset points to the start of APK Signing Block.
        // We thus modify the EoCD accordingly.
        ByteBuffer modifiedEocd = ByteBuffer.allocate(eocd.remaining());
        modifiedEocd.order(ByteOrder.LITTLE_ENDIAN);
        modifiedEocd.put(eocd);
        modifiedEocd.flip();
        ZipUtils.setZipEocdCentralDirectoryOffset(modifiedEocd, beforeApkSigningBlock.size());
        Map<ContentDigestAlgorithm, byte[]> actualContentDigests;
        try {
            actualContentDigests =
                    V2SchemeSigner.computeContentDigests(
                            contentDigestAlgorithms,
                            new DataSource[] {
                                    beforeApkSigningBlock,
                                    centralDir,
                                    new ByteBufferDataSource(modifiedEocd)
                            });
        } catch (DigestException e) {
            throw new RuntimeException("Failed to compute content digests", e);
        }
        if (!contentDigestAlgorithms.equals(actualContentDigests.keySet())) {
            throw new RuntimeException(
                    "Mismatch between sets of requested and computed content digests"
                            + " . Requested: " + contentDigestAlgorithms
                            + ", computed: " + actualContentDigests.keySet());
        }

        // Compare digests computed over the rest of APK against the corresponding expected digests
        // in signer blocks.
        for (Result.SignerInfo signerInfo : result.signers) {
            for (Result.SignerInfo.ContentDigest expected : signerInfo.contentDigests) {
                SignatureAlgorithm signatureAlgorithm =
                        SignatureAlgorithm.findById(expected.getSignatureAlgorithmId());
                if (signatureAlgorithm == null) {
                    continue;
                }
                ContentDigestAlgorithm contentDigestAlgorithm =
                        signatureAlgorithm.getContentDigestAlgorithm();
                byte[] expectedDigest = expected.getValue();
                byte[] actualDigest = actualContentDigests.get(contentDigestAlgorithm);
                if (!Arrays.equals(expectedDigest, actualDigest)) {
                    signerInfo.addError(
                            Issue.V2_SIG_APK_DIGEST_DID_NOT_VERIFY,
                            contentDigestAlgorithm,
                            toHex(expectedDigest),
                            toHex(actualDigest));
                    continue;
                }
                signerInfo.verifiedContentDigests.put(contentDigestAlgorithm, actualDigest);
            }
        }
    }

    /**
     * APK Signature Scheme v2 block and additional information relevant to verifying the signatures
     * contained in the block against the file.
     */
    private static class SignatureInfo {
        /** Contents of APK Signature Scheme v2 block. */
        private final ByteBuffer signatureBlock;

        /** Position of the APK Signing Block in the file. */
        private final long apkSigningBlockOffset;

        /** Position of the ZIP Central Directory in the file. */
        private final long centralDirOffset;

        /** Position of the ZIP End of Central Directory (EoCD) in the file. */
        private final long eocdOffset;

        /** Contents of ZIP End of Central Directory (EoCD) of the file. */
        private final ByteBuffer eocd;

        private SignatureInfo(
                ByteBuffer signatureBlock,
                long apkSigningBlockOffset,
                long centralDirOffset,
                long eocdOffset,
                ByteBuffer eocd) {
            this.signatureBlock = signatureBlock;
            this.apkSigningBlockOffset = apkSigningBlockOffset;
            this.centralDirOffset = centralDirOffset;
            this.eocdOffset = eocdOffset;
            this.eocd = eocd;
        }
    }

    /**
     * Returns the APK Signature Scheme v2 block contained in the provided APK file and the
     * additional information relevant for verifying the block against the file.
     *
     * @throws SignatureNotFoundException if the APK is not signed using APK Signature Scheme v2
     * @throws IOException if an I/O error occurs while reading the APK
     */
    private static SignatureInfo findSignature(
            DataSource apk, ApkUtils.ZipSections zipSections, Result result)
                    throws IOException, SignatureNotFoundException {
        // Find the APK Signing Block. The block immediately precedes the Central Directory.
        ByteBuffer eocd = zipSections.getZipEndOfCentralDirectory();
        Pair<DataSource, Long> apkSigningBlockAndOffset = findApkSigningBlock(apk, zipSections);
        DataSource apkSigningBlock = apkSigningBlockAndOffset.getFirst();
        long apkSigningBlockOffset = apkSigningBlockAndOffset.getSecond();
        ByteBuffer apkSigningBlockBuf =
                apkSigningBlock.getByteBuffer(0, (int) apkSigningBlock.size());
        apkSigningBlockBuf.order(ByteOrder.LITTLE_ENDIAN);

        // Find the APK Signature Scheme v2 Block inside the APK Signing Block.
        ByteBuffer apkSignatureSchemeV2Block =
                findApkSignatureSchemeV2Block(apkSigningBlockBuf, result);

        return new SignatureInfo(
                apkSignatureSchemeV2Block,
                apkSigningBlockOffset,
                zipSections.getZipCentralDirectoryOffset(),
                zipSections.getZipEndOfCentralDirectoryOffset(),
                eocd);
    }

    /**
     * Returns the APK Signing Block and its offset in the provided APK.
     *
     * @throws SignatureNotFoundException if the APK does not contain an APK Signing Block
     */
    public static Pair<DataSource, Long> findApkSigningBlock(
            DataSource apk, ApkUtils.ZipSections zipSections)
                    throws IOException, SignatureNotFoundException {
        // FORMAT:
        // OFFSET       DATA TYPE  DESCRIPTION
        // * @+0  bytes uint64:    size in bytes (excluding this field)
        // * @+8  bytes payload
        // * @-24 bytes uint64:    size in bytes (same as the one above)
        // * @-16 bytes uint128:   magic

        long centralDirStartOffset = zipSections.getZipCentralDirectoryOffset();
        long centralDirEndOffset =
                centralDirStartOffset + zipSections.getZipCentralDirectorySizeBytes();
        long eocdStartOffset = zipSections.getZipEndOfCentralDirectoryOffset();
        if (centralDirEndOffset != eocdStartOffset) {
            throw new SignatureNotFoundException(
                    "ZIP Central Directory is not immediately followed by End of Central Directory"
                            + ". CD end: " + centralDirEndOffset
                            + ", EoCD start: " + eocdStartOffset);
        }

        if (centralDirStartOffset < APK_SIG_BLOCK_MIN_SIZE) {
            throw new SignatureNotFoundException(
                    "APK too small for APK Signing Block. ZIP Central Directory offset: "
                            + centralDirStartOffset);
        }
        // Read the magic and offset in file from the footer section of the block:
        // * uint64:   size of block
        // * 16 bytes: magic
        ByteBuffer footer = apk.getByteBuffer(centralDirStartOffset - 24, 24);
        footer.order(ByteOrder.LITTLE_ENDIAN);
        if ((footer.getLong(8) != APK_SIG_BLOCK_MAGIC_LO)
                || (footer.getLong(16) != APK_SIG_BLOCK_MAGIC_HI)) {
            throw new SignatureNotFoundException(
                    "No APK Signing Block before ZIP Central Directory");
        }
        // Read and compare size fields
        long apkSigBlockSizeInFooter = footer.getLong(0);
        if ((apkSigBlockSizeInFooter < footer.capacity())
                || (apkSigBlockSizeInFooter > Integer.MAX_VALUE - 8)) {
            throw new SignatureNotFoundException(
                    "APK Signing Block size out of range: " + apkSigBlockSizeInFooter);
        }
        int totalSize = (int) (apkSigBlockSizeInFooter + 8);
        long apkSigBlockOffset = centralDirStartOffset - totalSize;
        if (apkSigBlockOffset < 0) {
            throw new SignatureNotFoundException(
                    "APK Signing Block offset out of range: " + apkSigBlockOffset);
        }
        ByteBuffer apkSigBlock = apk.getByteBuffer(apkSigBlockOffset, 8);
        apkSigBlock.order(ByteOrder.LITTLE_ENDIAN);
        long apkSigBlockSizeInHeader = apkSigBlock.getLong(0);
        if (apkSigBlockSizeInHeader != apkSigBlockSizeInFooter) {
            throw new SignatureNotFoundException(
                    "APK Signing Block sizes in header and footer do not match: "
                            + apkSigBlockSizeInHeader + " vs " + apkSigBlockSizeInFooter);
        }
        return Pair.of(apk.slice(apkSigBlockOffset, totalSize), apkSigBlockOffset);
    }

    private static ByteBuffer findApkSignatureSchemeV2Block(
            ByteBuffer apkSigningBlock,
            Result result) throws SignatureNotFoundException {
        checkByteOrderLittleEndian(apkSigningBlock);
        // FORMAT:
        // OFFSET       DATA TYPE  DESCRIPTION
        // * @+0  bytes uint64:    size in bytes (excluding this field)
        // * @+8  bytes pairs
        // * @-24 bytes uint64:    size in bytes (same as the one above)
        // * @-16 bytes uint128:   magic
        ByteBuffer pairs = sliceFromTo(apkSigningBlock, 8, apkSigningBlock.capacity() - 24);

        int entryCount = 0;
        while (pairs.hasRemaining()) {
            entryCount++;
            if (pairs.remaining() < 8) {
                throw new SignatureNotFoundException(
                        "Insufficient data to read size of APK Signing Block entry #" + entryCount);
            }
            long lenLong = pairs.getLong();
            if ((lenLong < 4) || (lenLong > Integer.MAX_VALUE)) {
                throw new SignatureNotFoundException(
                        "APK Signing Block entry #" + entryCount
                                + " size out of range: " + lenLong);
            }
            int len = (int) lenLong;
            int nextEntryPos = pairs.position() + len;
            if (len > pairs.remaining()) {
                throw new SignatureNotFoundException(
                        "APK Signing Block entry #" + entryCount + " size out of range: " + len
                                + ", available: " + pairs.remaining());
            }
            int id = pairs.getInt();
            if (id == APK_SIGNATURE_SCHEME_V2_BLOCK_ID) {
                return getByteBuffer(pairs, len - 4);
            }
            result.addWarning(Issue.APK_SIG_BLOCK_UNKNOWN_ENTRY_ID, id);
            pairs.position(nextEntryPos);
        }

        throw new SignatureNotFoundException(
                "No APK Signature Scheme v2 block in APK Signing Block");
    }

    private static void checkByteOrderLittleEndian(ByteBuffer buffer) {
        if (buffer.order() != ByteOrder.LITTLE_ENDIAN) {
            throw new IllegalArgumentException("ByteBuffer byte order must be little endian");
        }
    }

    public static class SignatureNotFoundException extends Exception {
        private static final long serialVersionUID = 1L;

        public SignatureNotFoundException(String message) {
            super(message);
        }

        public SignatureNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Returns new byte buffer whose content is a shared subsequence of this buffer's content
     * between the specified start (inclusive) and end (exclusive) positions. As opposed to
     * {@link ByteBuffer#slice()}, the returned buffer's byte order is the same as the source
     * buffer's byte order.
     */
    private static ByteBuffer sliceFromTo(ByteBuffer source, int start, int end) {
        if (start < 0) {
            throw new IllegalArgumentException("start: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end < start: " + end + " < " + start);
        }
        int capacity = source.capacity();
        if (end > source.capacity()) {
            throw new IllegalArgumentException("end > capacity: " + end + " > " + capacity);
        }
        int originalLimit = source.limit();
        int originalPosition = source.position();
        try {
            source.position(0);
            source.limit(end);
            source.position(start);
            ByteBuffer result = source.slice();
            result.order(source.order());
            return result;
        } finally {
            source.position(0);
            source.limit(originalLimit);
            source.position(originalPosition);
        }
    }

    /**
     * Relative <em>get</em> method for reading {@code size} number of bytes from the current
     * position of this buffer.
     *
     * <p>This method reads the next {@code size} bytes at this buffer's current position,
     * returning them as a {@code ByteBuffer} with start set to 0, limit and capacity set to
     * {@code size}, byte order set to this buffer's byte order; and then increments the position by
     * {@code size}.
     */
    private static ByteBuffer getByteBuffer(ByteBuffer source, int size)
            throws BufferUnderflowException {
        if (size < 0) {
            throw new IllegalArgumentException("size: " + size);
        }
        int originalLimit = source.limit();
        int position = source.position();
        int limit = position + size;
        if ((limit < position) || (limit > originalLimit)) {
            throw new BufferUnderflowException();
        }
        source.limit(limit);
        try {
            ByteBuffer result = source.slice();
            result.order(source.order());
            source.position(limit);
            return result;
        } finally {
            source.limit(originalLimit);
        }
    }

    private static ByteBuffer getLengthPrefixedSlice(ByteBuffer source) throws ApkFormatException {
        if (source.remaining() < 4) {
            throw new ApkFormatException(
                    "Remaining buffer too short to contain length of length-prefixed field"
                            + ". Remaining: " + source.remaining());
        }
        int len = source.getInt();
        if (len < 0) {
            throw new IllegalArgumentException("Negative length");
        } else if (len > source.remaining()) {
            throw new ApkFormatException(
                    "Length-prefixed field longer than remaining buffer"
                            + ". Field length: " + len + ", remaining: " + source.remaining());
        }
        return getByteBuffer(source, len);
    }

    private static byte[] readLengthPrefixedByteArray(ByteBuffer buf) throws ApkFormatException {
        int len = buf.getInt();
        if (len < 0) {
            throw new ApkFormatException("Negative length");
        } else if (len > buf.remaining()) {
            throw new ApkFormatException(
                    "Underflow while reading length-prefixed value. Length: " + len
                            + ", available: " + buf.remaining());
        }
        byte[] result = new byte[len];
        buf.get(result);
        return result;
    }

    /**
     * {@link X509Certificate} whose {@link #getEncoded()} returns the data provided at construction
     * time.
     */
    private static class GuaranteedEncodedFormX509Certificate extends DelegatingX509Certificate {
        private byte[] mEncodedForm;

        public GuaranteedEncodedFormX509Certificate(X509Certificate wrapped, byte[] encodedForm) {
            super(wrapped);
            this.mEncodedForm = (encodedForm != null) ? encodedForm.clone() : null;
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return (mEncodedForm != null) ? mEncodedForm.clone() : null;
        }
    }

    private static final char[] HEX_DIGITS = "01234567890abcdef".toCharArray();

    private static String toHex(byte[] value) {
        StringBuilder sb = new StringBuilder(value.length * 2);
        int len = value.length;
        for (int i = 0; i < len; i++) {
            int hi = (value[i] & 0xff) >>> 4;
            int lo = value[i] & 0x0f;
            sb.append(HEX_DIGITS[hi]).append(HEX_DIGITS[lo]);
        }
        return sb.toString();
    }

    public static class Result {

        /** Whether the APK's APK Signature Scheme v2 signature verifies. */
        public boolean verified;

        public final List<SignerInfo> signers = new ArrayList<>();
        private final List<IssueWithParams> mWarnings = new ArrayList<>();
        private final List<IssueWithParams> mErrors = new ArrayList<>();

        public boolean containsErrors() {
            if (!mErrors.isEmpty()) {
                return true;
            }
            if (!signers.isEmpty()) {
                for (SignerInfo signer : signers) {
                    if (signer.containsErrors()) {
                        return true;
                    }
                }
            }
            return false;
        }

        public void addError(Issue msg, Object... parameters) {
            mErrors.add(new IssueWithParams(msg, parameters));
        }

        public void addWarning(Issue msg, Object... parameters) {
            mWarnings.add(new IssueWithParams(msg, parameters));
        }

        public List<IssueWithParams> getErrors() {
            return mErrors;
        }

        public List<IssueWithParams> getWarnings() {
            return mWarnings;
        }

        public static class SignerInfo {
            public int index;
            public List<X509Certificate> certs = new ArrayList<>();
            public List<ContentDigest> contentDigests = new ArrayList<>();
            public Map<ContentDigestAlgorithm, byte[]> verifiedContentDigests = new HashMap<>();
            public List<Signature> signatures = new ArrayList<>();
            public Map<SignatureAlgorithm, byte[]> verifiedSignatures = new HashMap<>();
            public List<AdditionalAttribute> additionalAttributes = new ArrayList<>();
            public byte[] signedData;

            private final List<IssueWithParams> mWarnings = new ArrayList<>();
            private final List<IssueWithParams> mErrors = new ArrayList<>();

            public void addError(Issue msg, Object... parameters) {
                mErrors.add(new IssueWithParams(msg, parameters));
            }

            public void addWarning(Issue msg, Object... parameters) {
                mWarnings.add(new IssueWithParams(msg, parameters));
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

            public static class ContentDigest {
                private final int mSignatureAlgorithmId;
                private final byte[] mValue;

                public ContentDigest(int signatureAlgorithmId, byte[] value) {
                    mSignatureAlgorithmId  = signatureAlgorithmId;
                    mValue = value;
                }

                public int getSignatureAlgorithmId() {
                    return mSignatureAlgorithmId;
                }

                public byte[] getValue() {
                    return mValue;
                }
            }

            public static class Signature {
                private final int mAlgorithmId;
                private final byte[] mValue;

                public Signature(int algorithmId, byte[] value) {
                    mAlgorithmId  = algorithmId;
                    mValue = value;
                }

                public int getAlgorithmId() {
                    return mAlgorithmId;
                }

                public byte[] getValue() {
                    return mValue;
                }
            }

            public static class AdditionalAttribute {
                private final int mId;
                private final byte[] mValue;

                public AdditionalAttribute(int id, byte[] value) {
                    mId  = id;
                    mValue = value.clone();
                }

                public int getId() {
                    return mId;
                }

                public byte[] getValue() {
                    return mValue.clone();
                }
            }
        }
    }
}
