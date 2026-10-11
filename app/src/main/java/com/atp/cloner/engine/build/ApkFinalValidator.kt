package com.atp.cloner.engine.build

import com.atp.cloner.engine.AXmlDecoder
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class FinalValidationResult(
    val isInstallReady: Boolean,
    val verifiedPackageId: String,
    val verifiedAppName: String,
    val checksumSha256: String,
    val fileSize: Long,
    val issues: List<String> = emptyList()
)

class ApkFinalValidator {

    private val aXmlDecoder = AXmlDecoder()

    fun validateFinalApk(
        apkFile: File,
        expectedPackageId: String,
        expectedAppName: String
    ): FinalValidationResult {
        val issues = mutableListOf<String>()

        if (!apkFile.exists() || apkFile.length() == 0L) {
            return FinalValidationResult(
                isInstallReady = false,
                verifiedPackageId = "",
                verifiedAppName = "",
                checksumSha256 = "",
                fileSize = 0L,
                issues = listOf("Output APK file does not exist or is empty")
            )
        }

        var detectedPackage = expectedPackageId
        var detectedAppLabel = expectedAppName
        var hasManifest = false
        var hasDex = false
        var hasMetaInf = false

        try {
            ZipFile(apkFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                if (manifestEntry != null) {
                    hasManifest = true
                    val bytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
                    val decoded = aXmlDecoder.decodeManifest(bytes)
                    if (decoded.packageName.isNotEmpty()) detectedPackage = decoded.packageName
                    if (decoded.appLabel.isNotEmpty()) detectedAppLabel = decoded.appLabel
                } else {
                    issues.add("Missing AndroidManifest.xml")
                }

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".dex")) hasDex = true
                    if (entry.name.startsWith("META-INF/")) hasMetaInf = true
                }
            }
        } catch (e: Exception) {
            issues.add("Archive verification error: ${e.message}")
        }

        if (!hasDex) issues.add("Warning: No DEX files found in APK archive")
        if (!hasMetaInf) issues.add("Critical: Missing META-INF signature files")

        // Calculate SHA-256 checksum
        val checksum = calculateSha256(apkFile)

        return FinalValidationResult(
            isInstallReady = issues.none { it.startsWith("Critical") } && hasManifest,
            verifiedPackageId = detectedPackage,
            verifiedAppName = detectedAppLabel,
            checksumSha256 = checksum,
            fileSize = apkFile.length(),
            issues = issues
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
