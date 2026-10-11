package com.atp.cloner.engine.build

import com.atp.cloner.engine.AXmlDecoder
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class FinalValidationResult(
    val isValid: Boolean,
    val verifiedPackageName: String,
    val verifiedAppName: String,
    val fileSizeBytes: Long,
    val sha256Checksum: String,
    val isInstallReady: Boolean,
    val signatureVerified: Boolean,
    val dexVerified: Boolean,
    val details: String,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

class ApkFinalValidator {

    private val aXmlDecoder = AXmlDecoder()
    private val dexValidator = ApkDexValidator()
    private val signatureVerifier = ApkSignatureVerifier()

    /**
     * Conducts comprehensive final verification on the rebuilt, signed, and aligned APK.
     */
    fun validateFinalApk(
        rebuiltApkFile: File,
        expectedPackageName: String,
        expectedAppName: String
    ): FinalValidationResult {
        if (!rebuiltApkFile.exists() || rebuiltApkFile.length() == 0L) {
            return FinalValidationResult(
                isValid = false,
                verifiedPackageName = "",
                verifiedAppName = "",
                fileSizeBytes = 0L,
                sha256Checksum = "",
                isInstallReady = false,
                signatureVerified = false,
                dexVerified = false,
                details = "Output APK file not found or empty.",
                errors = listOf("File not found: ${rebuiltApkFile.absolutePath}")
            )
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var verifiedPackage = ""
        var verifiedApp = ""

        // 1. Verify Manifest
        try {
            ZipFile(rebuiltApkFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                if (manifestEntry == null) {
                    errors.add("AndroidManifest.xml missing from output APK")
                } else {
                    val bytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
                    val decoded = aXmlDecoder.decodeManifest(bytes)
                    verifiedPackage = decoded.packageName
                    verifiedApp = decoded.appLabel
                }
            }
        } catch (e: Exception) {
            errors.add("Failed reading output APK archive: ${e.message}")
        }

        // 2. Verify DEX files
        val dexResult = dexValidator.validateApkDexFiles(rebuiltApkFile)
        if (!dexResult.isValid) {
            errors.addAll(dexResult.errors)
        }

        // 3. Verify Signature
        val sigResult = signatureVerifier.verifyApkSignature(rebuiltApkFile)
        if (!sigResult.isValid) {
            errors.addAll(sigResult.errors)
            errors.addAll(sigResult.digestMismatches)
        }

        // 4. Calculate SHA-256
        val sha256 = calculateSha256(rebuiltApkFile)
        val fileSize = rebuiltApkFile.length()

        // 5. Compare package name and app name
        if (expectedPackageName.isNotEmpty() && verifiedPackage.isNotEmpty() && verifiedPackage != expectedPackageName) {
            warnings.add("Verified package '$verifiedPackage' differs from target '$expectedPackageName'")
        }

        val isValid = errors.isEmpty()
        val isInstallReady = isValid && sigResult.isValid && dexResult.isValid

        val details = if (isValid) {
            "Final verification passed: Valid ZIP, verified package '$verifiedPackage', DEX integrity intact, Scheme v1 signature valid."
        } else {
            "Final verification failed: ${errors.joinToString("; ")}"
        }

        return FinalValidationResult(
            isValid = isValid,
            verifiedPackageName = verifiedPackage.ifEmpty { expectedPackageName },
            verifiedAppName = verifiedApp.ifEmpty { expectedAppName },
            fileSizeBytes = fileSize,
            sha256Checksum = sha256,
            isInstallReady = isInstallReady,
            signatureVerified = sigResult.isValid,
            dexVerified = dexResult.isValid,
            details = details,
            warnings = warnings,
            errors = errors
        )
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(65536)
            var len: Int
            while (input.read(buf).also { len = it } != -1) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
