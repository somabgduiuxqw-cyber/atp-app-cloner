package com.atp.cloner.repository

import android.content.Context
import android.net.Uri
import com.atp.cloner.data.db.AppClonerDatabase
import com.atp.cloner.data.db.ProcessingLogEntity
import com.atp.cloner.data.db.ProjectEntity
import com.atp.cloner.data.model.ApkMetadataDiscoveryReport
import com.atp.cloner.engine.ApkMetadataDiscoveryEngine
import com.atp.cloner.engine.SampleApkGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val db = AppClonerDatabase.getDatabase(context)
    private val projectDao = db.projectDao()
    private val logDao = db.processingLogDao()

    private val discoveryEngine = ApkMetadataDiscoveryEngine(context)
    private val sampleApkGenerator = SampleApkGenerator(context)

    // Cached discovery reports in memory
    private val reportCache = mutableMapOf<String, ApkMetadataDiscoveryReport>()

    val allProjectsFlow: Flow<List<ProjectEntity>> = projectDao.getAllProjectsFlow()
    val projectCountFlow: Flow<Int> = projectDao.getProjectCountFlow()
    val successCountFlow: Flow<Int> = projectDao.getSuccessCountFlow()
    val failedCountFlow: Flow<Int> = projectDao.getFailedCountFlow()

    suspend fun getProject(id: String): ProjectEntity? = projectDao.getProjectById(id)

    suspend fun importApkFromUri(uri: Uri, originalName: String): ProjectEntity = withContext(Dispatchers.IO) {
        val projectId = UUID.randomUUID().toString()
        val projectDir = File(context.filesDir, "projects/$projectId")
        if (!projectDir.exists()) projectDir.mkdirs()

        val localApk = File(projectDir, "original.apk")

        // Stream file safely from ContentResolver
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(localApk).use { output ->
                val buffer = ByteArray(32768)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        } ?: throw IllegalStateException("Unable to open input stream for URI: $uri")

        val sha256 = calculateSha256(localApk)

        // Run deep discovery
        val report = discoveryEngine.discoverMetadata(localApk, projectId)
        reportCache[projectId] = report

        // Save preview icon to file if available
        var iconPath: String? = null
        report.launcherIconDiscovery.previewIconBytes?.let { bytes ->
            val iconFile = File(projectDir, "icon.png")
            FileOutputStream(iconFile).use { it.write(bytes) }
            iconPath = iconFile.absolutePath
        }

        val project = ProjectEntity(
            id = projectId,
            projectName = report.appNameDiscovery.resolvedDefaultLabel.ifEmpty { originalName.substringBeforeLast(".") },
            originalFileName = originalName,
            originalFilePath = localApk.absolutePath,
            originalFileSize = localApk.length(),
            originalSha256 = sha256,
            originalPackageName = report.packageDiscovery.manifestPackage,
            originalAppLabel = report.appNameDiscovery.resolvedDefaultLabel,
            originalVersionName = "1.0",
            originalVersionCode = 1L,
            iconCachedPath = iconPath,
            hasAdaptiveIcon = report.launcherIconDiscovery.resolvedIconType == "Adaptive XML",
            dexFileCount = report.packageDiscovery.dexReferences.map { it.dexFileName }.distinct().size.coerceAtLeast(1),
            nativeAbis = report.packageDiscovery.nativeLibraries.map { it.abi }.distinct().joinToString(", "),
            hasSignature = true,
            certFingerprint = null,
            decompilationStatus = "Discovered",
            enableNameChange = false,
            modifiedAppName = report.appNameDiscovery.resolvedDefaultLabel,
            enablePackageChange = false,
            modifiedPackageName = "${report.packageDiscovery.manifestPackage}.clone",
            enableIconChange = false,
            customIconPath = null,
            lastStatus = "Discovered",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        projectDao.insertProject(project)

        // Save discovery logs
        val logs = report.scanLogs.map { msg ->
            ProcessingLogEntity(
                projectId = projectId,
                stage = "Metadata Discovery",
                message = msg,
                status = "INFO"
            )
        }
        logDao.insertLogs(logs)

        project
    }

    suspend fun createAndImportSampleApk(): ProjectEntity = withContext(Dispatchers.IO) {
        val sampleFile = sampleApkGenerator.createSampleApk()
        val projectId = UUID.randomUUID().toString()
        val projectDir = File(context.filesDir, "projects/$projectId")
        if (!projectDir.exists()) projectDir.mkdirs()

        val localApk = File(projectDir, "original.apk")
        sampleFile.copyTo(localApk, overwrite = true)

        val sha256 = calculateSha256(localApk)
        val report = discoveryEngine.discoverMetadata(localApk, projectId)
        reportCache[projectId] = report

        var iconPath: String? = null
        report.launcherIconDiscovery.previewIconBytes?.let { bytes ->
            val iconFile = File(projectDir, "icon.png")
            FileOutputStream(iconFile).use { it.write(bytes) }
            iconPath = iconFile.absolutePath
        }

        val project = ProjectEntity(
            id = projectId,
            projectName = "Sample App (Demo)",
            originalFileName = "ATP_Sample_App_v1.0.apk",
            originalFilePath = localApk.absolutePath,
            originalFileSize = localApk.length(),
            originalSha256 = sha256,
            originalPackageName = report.packageDiscovery.manifestPackage,
            originalAppLabel = report.appNameDiscovery.resolvedDefaultLabel,
            originalVersionName = "1.0.0",
            originalVersionCode = 1L,
            iconCachedPath = iconPath,
            hasAdaptiveIcon = true,
            dexFileCount = 1,
            nativeAbis = "None (Universal Pure DEX)",
            hasSignature = true,
            certFingerprint = "42:80:8C:C0:FA:AD:4F:73:A9:A6:B6:9C:8D:2D:D2:D2",
            decompilationStatus = "Discovered",
            enableNameChange = false,
            modifiedAppName = "Sample App Cloned",
            enablePackageChange = false,
            modifiedPackageName = "com.atp.sampleapp.clone",
            enableIconChange = false,
            lastStatus = "Discovered",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        projectDao.insertProject(project)

        val logs = report.scanLogs.map { msg ->
            ProcessingLogEntity(
                projectId = projectId,
                stage = "Sample Discovery",
                message = msg,
                status = "INFO"
            )
        }
        logDao.insertLogs(logs)

        project
    }

    suspend fun getDiscoveryReport(projectId: String): ApkMetadataDiscoveryReport? = withContext(Dispatchers.IO) {
        reportCache[projectId]?.let { return@withContext it }

        val project = projectDao.getProjectById(projectId) ?: return@withContext null
        val apkFile = File(project.originalFilePath)
        if (!apkFile.exists()) return@withContext null

        val report = discoveryEngine.discoverMetadata(apkFile, projectId)
        reportCache[projectId] = report
        report
    }

    suspend fun importInstalledApp(appInfo: com.atp.cloner.data.model.InstalledAppInfo): ProjectEntity = withContext(Dispatchers.IO) {
        val projectId = UUID.randomUUID().toString()
        val projectDir = File(context.filesDir, "projects/$projectId")
        if (!projectDir.exists()) projectDir.mkdirs()

        val extractionService = com.atp.cloner.engine.ApkExtractionService(context)
        val extractionResult = extractionService.extractInstalledApp(appInfo, projectDir)
        if (!extractionResult.success || extractionResult.extractedApkFile == null) {
            throw IllegalStateException(extractionResult.errorMessage ?: "Failed to extract installed app APK")
        }

        val localApk = extractionResult.extractedApkFile
        val sha256 = extractionResult.sha256Checksum

        val report = discoveryEngine.discoverMetadata(localApk, projectId)
        reportCache[projectId] = report

        var iconPath: String? = null
        report.launcherIconDiscovery.previewIconBytes?.let { bytes ->
            val iconFile = File(projectDir, "icon.png")
            FileOutputStream(iconFile).use { it.write(bytes) }
            iconPath = iconFile.absolutePath
        }

        val project = ProjectEntity(
            id = projectId,
            projectName = appInfo.appName.ifEmpty { report.appNameDiscovery.resolvedDefaultLabel },
            originalFileName = "${appInfo.packageName}.apk",
            originalFilePath = localApk.absolutePath,
            originalFileSize = localApk.length(),
            originalSha256 = sha256,
            originalPackageName = appInfo.packageName,
            originalAppLabel = appInfo.appName,
            originalVersionName = appInfo.versionName,
            originalVersionCode = appInfo.versionCode,
            iconCachedPath = iconPath,
            hasAdaptiveIcon = report.launcherIconDiscovery.resolvedIconType == "Adaptive XML",
            dexFileCount = report.packageDiscovery.dexReferences.map { it.dexFileName }.distinct().size.coerceAtLeast(1),
            nativeAbis = report.packageDiscovery.nativeLibraries.map { it.abi }.distinct().joinToString(", ").ifEmpty { "Universal" },
            hasSignature = true,
            certFingerprint = null,
            decompilationStatus = "Discovered",
            enableNameChange = false,
            modifiedAppName = appInfo.appName,
            enablePackageChange = false,
            modifiedPackageName = "${appInfo.packageName}.clone",
            enableIconChange = false,
            customIconPath = null,
            lastStatus = if (appInfo.isSplitApk) "Extracted (Split APKs detected: ${appInfo.splitCount})" else "Extracted",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        projectDao.insertProject(project)

        val logs = report.scanLogs.map { msg ->
            ProcessingLogEntity(
                projectId = projectId,
                stage = "Installed App Discovery",
                message = msg,
                status = "INFO"
            )
        }.toMutableList()

        if (appInfo.isSplitApk) {
            logs.add(
                ProcessingLogEntity(
                    projectId = projectId,
                    stage = "Split APK Detection",
                    message = "Application has ${appInfo.splitCount} split APKs: ${appInfo.splitNames.joinToString(", ")}. Base APK extracted.",
                    status = "WARNING"
                )
            )
        }
        logDao.insertLogs(logs)

        project
    }

    suspend fun updateProject(project: ProjectEntity) = projectDao.updateProject(project)

    suspend fun deleteProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
        val projectDir = File(context.filesDir, "projects/${project.id}")
        if (projectDir.exists()) projectDir.deleteRecursively()
        reportCache.remove(project.id)
        logDao.deleteLogsForProject(project.id)
        projectDao.deleteProject(project)
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
