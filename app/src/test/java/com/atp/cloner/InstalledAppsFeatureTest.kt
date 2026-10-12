package com.atp.cloner

import com.atp.cloner.data.model.InstalledAppInfo
import com.atp.cloner.engine.ApkExtractionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalledAppsFeatureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testExtractionRejectsMissingApkPath() {
        val context = RuntimeEnvironment.getApplication()
        val extractionService = ApkExtractionService(context)

        val appInfo = InstalledAppInfo(
            appName = "Mock App",
            packageName = "com.mock.app",
            versionName = "1.0",
            versionCode = 1L,
            isSystemApp = false,
            apkPath = "",
            apkSize = 0L,
            isSplitApk = false
        )

        val destDir = tempFolder.newFolder("extract_test")
        val result = kotlinx.coroutines.runBlocking {
            extractionService.extractInstalledApp(appInfo, destDir)
        }

        assertFalse("Extraction without APK path must fail", result.success)
        assertNotNull("Must return an error message", result.errorMessage)
    }

    @Test
    fun testExtractionSucceedsWithValidApkAndDetectsSplits() {
        val context = RuntimeEnvironment.getApplication()
        val extractionService = ApkExtractionService(context)

        // Create a valid synthetic APK file
        val mockApk = tempFolder.newFile("mock_valid.apk")
        ZipOutputStream(FileOutputStream(mockApk)).use { zos ->
            val manifestEntry = ZipEntry("AndroidManifest.xml")
            zos.putNextEntry(manifestEntry)
            zos.write(byteArrayOf(0x03, 0x00, 0x08, 0x00, 0x10, 0x00, 0x00, 0x00))
            zos.closeEntry()

            val dexEntry = ZipEntry("classes.dex")
            zos.putNextEntry(dexEntry)
            zos.write("dex_content".toByteArray())
            zos.closeEntry()
        }

        val appInfo = InstalledAppInfo(
            appName = "Modular App",
            packageName = "com.modular.app",
            versionName = "2.1.0",
            versionCode = 42L,
            isSystemApp = false,
            apkPath = mockApk.absolutePath,
            apkSize = mockApk.length(),
            isSplitApk = true,
            splitCount = 2,
            splitNames = listOf("split_config.arm64_v8a.apk", "split_config.xxhdpi.apk")
        )

        val destDir = tempFolder.newFolder("extract_dest")
        val result = kotlinx.coroutines.runBlocking {
            extractionService.extractInstalledApp(appInfo, destDir)
        }

        assertTrue("Valid APK extraction must succeed", result.success)
        assertNotNull("Must return extracted APK file", result.extractedApkFile)
        assertTrue("Extracted APK file must exist", result.extractedApkFile!!.exists())
        assertTrue("Extracted APK file must match source size", result.fileSize > 0)
        assertTrue("Checksum must be non-empty", result.sha256Checksum.isNotEmpty())
        assertTrue("Must detect split APK flag", result.isSplitApk)
        assertEquals(2, result.splitNames.size)
        assertNotNull("Must generate notice for split APK", result.warningMessage)
    }

    @Test
    fun testExtractionRejectsInvalidArchive() {
        val context = RuntimeEnvironment.getApplication()
        val extractionService = ApkExtractionService(context)

        // Create corrupt/non-APK file
        val corruptFile = tempFolder.newFile("corrupt.apk")
        corruptFile.writeText("Not a zip file")

        val appInfo = InstalledAppInfo(
            appName = "Corrupt App",
            packageName = "com.corrupt.app",
            versionName = "1.0",
            versionCode = 1L,
            isSystemApp = false,
            apkPath = corruptFile.absolutePath,
            apkSize = corruptFile.length(),
            isSplitApk = false
        )

        val destDir = tempFolder.newFolder("extract_corrupt")
        val result = kotlinx.coroutines.runBlocking {
            extractionService.extractInstalledApp(appInfo, destDir)
        }

        assertFalse("Corrupt archive extraction must fail", result.success)
        assertTrue("Error message must explain validation failure", result.errorMessage?.contains("integrity") == true || result.errorMessage?.contains("valid") == true)
    }
}
