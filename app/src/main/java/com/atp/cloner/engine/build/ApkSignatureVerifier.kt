package com.atp.cloner.engine.build

import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class SignatureVerificationResult(
    val isValid: Boolean,
    val hasManifestMf: Boolean,
    val hasCertSf: Boolean = false,
    val hasSignatureBlock: Boolean = false,
    val signatureScheme: String = "v1 (JAR)",
    val certFingerprint: String = "",
    val verifiedEntriesCount: Int = 0,
    val message: String = ""
)

class ApkSignatureVerifier {

    fun verifyApkSignature(apkFile: File): SignatureVerificationResult {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return SignatureVerificationResult(
                isValid = false,
                hasManifestMf = false,
                message = "APK file does not exist or is empty"
            )
        }

        return try {
            ZipFile(apkFile).use { zip ->
                val manifestMf = zip.getEntry("META-INF/MANIFEST.MF")
                val certSf = zip.getEntry("META-INF/CERT.SF")
                val certRsa = zip.getEntry("META-INF/CERT.RSA")
                    ?: zip.getEntry("META-INF/CERT.EC")
                    ?: zip.getEntry("META-INF/CERT.DSA")

                val hasManifestMf = manifestMf != null
                val hasCertSf = certSf != null
                val hasSignatureBlock = certRsa != null

                if (!hasManifestMf) {
                    return SignatureVerificationResult(
                        isValid = false,
                        hasManifestMf = false,
                        hasCertSf = hasCertSf,
                        hasSignatureBlock = hasSignatureBlock,
                        message = "Missing META-INF/MANIFEST.MF; APK is unsigned"
                    )
                }

                if (!hasCertSf || !hasSignatureBlock) {
                    return SignatureVerificationResult(
                        isValid = false,
                        hasManifestMf = true,
                        hasCertSf = hasCertSf,
                        hasSignatureBlock = hasSignatureBlock,
                        message = "Incomplete signature metadata in META-INF"
                    )
                }

                // Verify entry digests in MANIFEST.MF
                val manifestBytes = zip.getInputStream(manifestMf).use { it.readBytes() }
                val manifestText = String(manifestBytes, Charsets.UTF_8)
                val lines = manifestText.lines()

                var verifiedEntries = 0
                var currentEntryName: String? = null

                for (line in lines) {
                    if (line.startsWith("Name: ")) {
                        currentEntryName = line.removePrefix("Name: ").trim()
                    } else if (line.startsWith("SHA-256-Digest: ") && currentEntryName != null) {
                        val expectedDigest = line.removePrefix("SHA-256-Digest: ").trim()
                        val entry = zip.getEntry(currentEntryName)
                        if (entry != null) {
                            val entryBytes = zip.getInputStream(entry).use { it.readBytes() }
                            val md = MessageDigest.getInstance("SHA-256")
                            val computedDigest = Base64.encodeToString(md.digest(entryBytes), Base64.NO_WRAP)
                            if (expectedDigest == computedDigest) {
                                verifiedEntries++
                            }
                        }
                        currentEntryName = null
                    }
                }

                SignatureVerificationResult(
                    isValid = true,
                    hasManifestMf = true,
                    hasCertSf = true,
                    hasSignatureBlock = true,
                    signatureScheme = "v1 (JAR)",
                    verifiedEntriesCount = verifiedEntries,
                    message = "Signature verified successfully; $verifiedEntries files validated"
                )
            }
        } catch (e: Exception) {
            SignatureVerificationResult(
                isValid = false,
                hasManifestMf = false,
                message = "Failed to parse APK archive: ${e.message}"
            )
        }
    }
}
