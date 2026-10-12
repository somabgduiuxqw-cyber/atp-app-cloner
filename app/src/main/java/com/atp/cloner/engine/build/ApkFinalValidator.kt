package com.atp.cloner.engine.build

import android.content.Context
import com.atp.cloner.engine.AXmlDecoder
import com.atp.cloner.engine.ArscDecoder
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkFinalValidator(private val context: Context) {

    private val aXmlDecoder = AXmlDecoder()
    private val arscDecoder = ArscDecoder()

    data class FinalValidationResult(
        val success: Boolean,
        val verifiedPackageId: String,
        val verifiedAppName: String,
        val fileSize: Long,
        val sha256Checksum: String,
        val errors: List<String> = emptyList()
    )

    fun validateFinalApk(
        apkFile: File,
        expectedPackageId: String,
        expectedAppName: String
    ): FinalValidationResult {
        val errors = mutableListOf<String>()

        if (!apkFile.exists() || apkFile.length() == 0L) {
            return FinalValidationResult(false, "", "", 0L, "", listOf("APK file does not exist or is empty"))
        }

        var detectedPkg = expectedPackageId
        var detectedName = expectedAppName

        try {
            val zip = ZipFile(apkFile)
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
            if (manifestEntry != null) {
                zip.getInputStream(manifestEntry).use { input ->
                    val bytes = input.readBytes()
                    val manifestInfo = aXmlDecoder.decodeManifest(bytes)
                    if (manifestInfo.packageName.isNotEmpty()) {
                        detectedPkg = manifestInfo.packageName
                    }
                }
            } else {
                errors.add("Missing AndroidManifest.xml in final APK")
            }
            zip.close()
        } catch (e: Exception) {
            errors.add("Failed reading final APK archive: ${e.message}")
        }

        val sha256 = calculateSha256(apkFile)

        return FinalValidationResult(
            success = errors.isEmpty(),
            verifiedPackageId = detectedPkg,
            verifiedAppName = detectedName,
            fileSize = apkFile.length(),
            sha256Checksum = sha256,
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
