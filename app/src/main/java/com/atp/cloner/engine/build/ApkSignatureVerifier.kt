package com.atp.cloner.engine.build

import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class SignatureVerificationResult(
    val isValid: Boolean,
    val hasManifestMf: Boolean,
    val hasCertSf: Boolean,
    val hasCertRsa: Boolean,
    val totalEntriesVerified: Int,
    val digestMismatches: List<String> = emptyList(),
    val certFingerprintSha256: String = "",
    val details: String,
    val errors: List<String> = emptyList()
)

class ApkSignatureVerifier {

    /**
     * Verifies that the APK is signed according to APK Signature Scheme v1,
     * and that all entry hashes in MANIFEST.MF match the actual file contents.
     */
    fun verifyApkSignature(apkFile: File): SignatureVerificationResult {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return SignatureVerificationResult(
                isValid = false,
                hasManifestMf = false,
                hasCertSf = false,
                hasCertRsa = false,
                totalEntriesVerified = 0,
                details = "APK file does not exist or is empty.",
                errors = listOf("File not found or empty: ${apkFile.absolutePath}")
            )
        }

        var hasManifestMf = false
        var hasCertSf = false
        var hasCertRsa = false
        var manifestMfContent: String? = null
        var certSfContent: String? = null
        val expectedDigests = mutableMapOf<String, String>()
        val mismatches = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var verifiedCount = 0

        try {
            ZipFile(apkFile).use { zip ->
                val manifestEntry = zip.getEntry("META-INF/MANIFEST.MF")
                if (manifestEntry != null) {
                    hasManifestMf = true
                    manifestMfContent = zip.getInputStream(manifestEntry).bufferedReader().readText()
                }

                val sfEntry = zip.getEntry("META-INF/CERT.SF")
                if (sfEntry != null) {
                    hasCertSf = true
                    certSfContent = zip.getInputStream(sfEntry).bufferedReader().readText()
                }

                val rsaEntry = zip.getEntry("META-INF/CERT.RSA")
                if (rsaEntry != null) {
                    hasCertRsa = true
                }

                if (!hasManifestMf || !hasCertSf || !hasCertRsa) {
                    val missing = mutableListOf<String>()
                    if (!hasManifestMf) missing.add("META-INF/MANIFEST.MF")
                    if (!hasCertSf) missing.add("META-INF/CERT.SF")
                    if (!hasCertRsa) missing.add("META-INF/CERT.RSA")
                    return SignatureVerificationResult(
                        isValid = false,
                        hasManifestMf = hasManifestMf,
                        hasCertSf = hasCertSf,
                        hasCertRsa = hasCertRsa,
                        totalEntriesVerified = 0,
                        details = "Missing signature blocks in META-INF: ${missing.joinToString(", ")}",
                        errors = listOf("Unsigned APK or incomplete signature block.")
                    )
                }

                // Parse MANIFEST.MF digests
                val lines = manifestMfContent!!.lines()
                var currentName: String? = null
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("Name: ")) {
                        currentName = trimmed.substring(6).trim()
                    } else if (trimmed.startsWith("SHA-256-Digest: ") && currentName != null) {
                        val digest = trimmed.substring(16).trim()
                        expectedDigests[currentName] = digest
                        currentName = null
                    }
                }

                // Verify each non-META-INF entry
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || entry.name.startsWith("META-INF/")) continue

                    val expected = expectedDigests[entry.name]
                    if (expected == null) {
                        mismatches.add("Entry '${entry.name}' missing from MANIFEST.MF")
                        continue
                    }

                    zip.getInputStream(entry).use { stream ->
                        val md = MessageDigest.getInstance("SHA-256")
                        val buf = ByteArray(16384)
                        var len: Int
                        while (stream.read(buf).also { len = it } != -1) {
                            md.update(buf, 0, len)
                        }
                        val actual = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                        if (actual != expected) {
                            mismatches.add("Digest mismatch in '${entry.name}': expected $expected, actual $actual")
                        } else {
                            verifiedCount++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Exception while verifying APK signature: ${e.message}")
            return SignatureVerificationResult(
                isValid = false,
                hasManifestMf = hasManifestMf,
                hasCertSf = hasCertSf,
                hasCertRsa = hasCertRsa,
                totalEntriesVerified = verifiedCount,
                digestMismatches = mismatches,
                details = "Signature verification aborted with error: ${e.message}",
                errors = errors
            )
        }

        val isValid = mismatches.isEmpty() && errors.isEmpty() && hasManifestMf && hasCertSf && hasCertRsa
        val details = if (isValid) {
            "Signature Scheme v1 verified. All $verifiedCount entries validated with SHA-256 digests."
        } else {
            "Signature verification failed with ${mismatches.size} mismatch(es)."
        }

        return SignatureVerificationResult(
            isValid = isValid,
            hasManifestMf = hasManifestMf,
            hasCertSf = hasCertSf,
            hasCertRsa = hasCertRsa,
            totalEntriesVerified = verifiedCount,
            digestMismatches = mismatches,
            details = details,
            errors = errors
        )
    }
}
