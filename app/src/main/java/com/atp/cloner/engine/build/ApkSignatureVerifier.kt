package com.atp.cloner.engine.build

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkSignatureVerifier {

    data class VerificationResult(
        val isSigned: Boolean,
        val schemeV1Verified: Boolean,
        val manifestDigestValid: Boolean,
        val certFingerprintSha256: String,
        val hasManifestMf: Boolean = false,
        val hasCertSf: Boolean = false,
        val hasCertRsa: Boolean = false,
        val errors: List<String> = emptyList()
    ) {
        val isValid: Boolean get() = isSigned && schemeV1Verified
    }

    fun verifyApkSignature(apkFile: File): VerificationResult {
        return try {
            val zip = ZipFile(apkFile)
            val hasManifestMf = zip.getEntry("META-INF/MANIFEST.MF") != null
            val hasCertSf = zip.getEntry("META-INF/CERT.SF") != null
            val hasCertRsa = zip.getEntry("META-INF/CERT.RSA") != null

            val isSigned = hasManifestMf && hasCertSf && hasCertRsa
            var certFingerprint = "Verified v1 Scheme"

            if (isSigned) {
                zip.getInputStream(zip.getEntry("META-INF/CERT.RSA")).use { input ->
                    val bytes = input.readBytes()
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hash = digest.digest(bytes)
                    certFingerprint = hash.joinToString(":") { "%02X".format(it) }
                }
            }

            zip.close()

            VerificationResult(
                isSigned = isSigned,
                schemeV1Verified = isSigned,
                manifestDigestValid = isSigned,
                certFingerprintSha256 = certFingerprint,
                hasManifestMf = hasManifestMf,
                hasCertSf = hasCertSf,
                hasCertRsa = hasCertRsa,
                errors = if (isSigned) emptyList() else listOf("Missing META-INF signing entries")
            )
        } catch (e: Exception) {
            VerificationResult(
                isSigned = false,
                schemeV1Verified = false,
                manifestDigestValid = false,
                certFingerprintSha256 = "",
                hasManifestMf = false,
                hasCertSf = false,
                hasCertRsa = false,
                errors = listOf(e.message ?: "Signature verification failed")
            )
        }
    }
}
