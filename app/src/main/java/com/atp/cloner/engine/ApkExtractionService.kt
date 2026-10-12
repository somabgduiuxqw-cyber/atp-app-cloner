package com.atp.cloner.engine

import android.content.Context
import com.atp.cloner.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkExtractionService(private val context: Context) {

    data class ExtractionResult(
        val success: Boolean,
        val extractedApkFile: File?,
        val sha256Checksum: String,
        val fileSize: Long,
        val isSplitApk: Boolean,
        val splitNames: List<String>,
        val errorMessage: String? = null,
        val warningMessage: String? = null
    )

    suspend fun extractInstalledApp(
        appInfo: InstalledAppInfo,
        destinationDirectory: File
    ): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            if (appInfo.apkPath.isBlank()) {
                return@withContext ExtractionResult(
                    success = false,
                    extractedApkFile = null,
                    sha256Checksum = "",
                    fileSize = 0L,
                    isSplitApk = appInfo.isSplitApk,
                    splitNames = appInfo.splitNames,
                    errorMessage = "APK path is unavailable for package: ${appInfo.packageName}."
                )
            }

            val sourceApk = File(appInfo.apkPath)
            if (!sourceApk.exists()) {
                return@withContext ExtractionResult(
                    success = false,
                    extractedApkFile = null,
                    sha256Checksum = "",
                    fileSize = 0L,
                    isSplitApk = appInfo.isSplitApk,
                    splitNames = appInfo.splitNames,
                    errorMessage = "Source APK file not found at ${appInfo.apkPath} for ${appInfo.packageName}."
                )
            }

            if (!destinationDirectory.exists()) {
                destinationDirectory.mkdirs()
            }

            val targetApk = File(destinationDirectory, "original.apk")

            // Copy APK safely to app's isolated private workspace
            try {
                FileInputStream(sourceApk).use { input ->
                    FileOutputStream(targetApk).use { output ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } catch (se: SecurityException) {
                return@withContext ExtractionResult(
                    success = false,
                    extractedApkFile = null,
                    sha256Checksum = "",
                    fileSize = 0L,
                    isSplitApk = appInfo.isSplitApk,
                    splitNames = appInfo.splitNames,
                    errorMessage = "Access to source APK blocked by system security policy for ${appInfo.packageName}. Try manual APK import."
                )
            } catch (ioe: java.io.IOException) {
                return@withContext ExtractionResult(
                    success = false,
                    extractedApkFile = null,
                    sha256Checksum = "",
                    fileSize = 0L,
                    isSplitApk = appInfo.isSplitApk,
                    splitNames = appInfo.splitNames,
                    errorMessage = "Failed reading APK (${ioe.localizedMessage ?: "Permission denied"}). Try manual APK import."
                )
            }

            // Verify copied file exists and is readable
            if (!targetApk.exists() || targetApk.length() == 0L) {
                return@withContext ExtractionResult(
                    success = false,
                    extractedApkFile = null,
                    sha256Checksum = "",
                    fileSize = 0L,
                    isSplitApk = appInfo.isSplitApk,
                    splitNames = appInfo.splitNames,
                    errorMessage = "Copied APK file is missing or empty."
                )
            }

            // Validate that the copied file is a valid APK ZIP archive
            try {
                val zip = ZipFile(targetApk)
                val hasManifest = zip.getEntry("AndroidManifest.xml") != null
                zip.close()
                if (!hasManifest) {
                    return@withContext ExtractionResult(
                        success = false,
                        extractedApkFile = null,
                        sha256Checksum = "",
                        fileSize = 0L,
                        isSplitApk = appInfo.isSplitApk,
                        splitNames = appInfo.splitNames,
                        errorMessage = "Extracted file is not a valid APK (missing AndroidManifest.xml)."
                    )
                }
            } catch (e: Exception) {
                return@withContext ExtractionResult(
                    success = false,
                    extractedApkFile = null,
                    sha256Checksum = "",
                    fileSize = 0L,
                    isSplitApk = appInfo.isSplitApk,
                    splitNames = appInfo.splitNames,
                    errorMessage = "Copied file failed APK archive integrity validation: ${e.message}"
                )
            }

            // Calculate SHA-256 Checksum
            val sha256 = calculateSha256(targetApk)

            val warning = if (appInfo.isSplitApk) {
                "Notice: ${appInfo.appName} uses App Bundles (${appInfo.splitCount} split APKs: ${appInfo.splitNames.joinToString(", ")}). Base APK extracted successfully. Cloning base APK works for standalone components; some device-split resources may be needed."
            } else null

            ExtractionResult(
                success = true,
                extractedApkFile = targetApk,
                sha256Checksum = sha256,
                fileSize = targetApk.length(),
                isSplitApk = appInfo.isSplitApk,
                splitNames = appInfo.splitNames,
                warningMessage = warning
            )
        } catch (e: Exception) {
            ExtractionResult(
                success = false,
                extractedApkFile = null,
                sha256Checksum = "",
                fileSize = 0L,
                isSplitApk = appInfo.isSplitApk,
                splitNames = appInfo.splitNames,
                errorMessage = "Extraction failed: ${e.message}. You can manually import an APK."
            )
        }
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
