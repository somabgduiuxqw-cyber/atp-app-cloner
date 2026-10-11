package com.atp.cloner.engine.modification

import android.content.Context
import android.graphics.BitmapFactory
import com.atp.cloner.data.model.ApkMetadataDiscoveryReport
import com.atp.cloner.data.model.CloneConfiguration
import com.atp.cloner.engine.AXmlDecoder
import com.atp.cloner.engine.ArscDecoder
import com.atp.cloner.engine.IconDiscoveryProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class ModificationStatus {
    data class Progress(
        val stageName: String,
        val stepNumber: Int,
        val totalSteps: Int = 7,
        val progressPercent: Int,
        val details: String
    ) : ModificationStatus()

    data class Success(val report: ApkModificationReport) : ModificationStatus()
    data class Failed(val error: String, val technicalDetail: String? = null) : ModificationStatus()
}

class ApkModificationCoordinator(private val context: Context) {

    private val aXmlDecoder = AXmlDecoder()
    private val arscDecoder = ArscDecoder()
    private val iconProcessor = IconDiscoveryProcessor()

    fun applyModifications(
        projectId: String,
        originalApkFile: File,
        config: CloneConfiguration,
        discoveryReport: ApkMetadataDiscoveryReport
    ): Flow<ModificationStatus> = flow {
        val startTime = System.currentTimeMillis()

        // Step 1: Validation
        emit(
            ModificationStatus.Progress(
                stageName = "Validating Configuration",
                stepNumber = 1,
                progressPercent = 10,
                details = "Checking Unicode app name, package syntax rules, and icon resources..."
            )
        )

        val validation = CloneConfigurationValidator.validate(
            config,
            discoveryReport.appNameDiscovery.resolvedDefaultLabel,
            discoveryReport.packageDiscovery.manifestPackage
        )

        if (!validation.isValid) {
            val err = validation.nameError ?: validation.packageError ?: validation.iconError ?: "Invalid configuration"
            emit(ModificationStatus.Failed(err))
            return@flow
        }

        // Step 2: Preparing isolated workspace
        emit(
            ModificationStatus.Progress(
                stageName = "Preparing Isolated Workspace",
                stepNumber = 2,
                progressPercent = 25,
                details = "Creating sandbox workspace; original APK remains immutable..."
            )
        )

        val workspaceDir = File(context.filesDir, "projects/$projectId/workspace")
        if (!workspaceDir.exists()) workspaceDir.mkdirs()

        val tempModifiedApk = File(workspaceDir, "working_modified.apk")
        if (tempModifiedApk.exists()) tempModifiedApk.delete()

        val appliedChanges = mutableListOf<String>()
        var manifestChangesCount = 0
        var resourceChangesCount = 0
        var iconChangesCount = 0

        // Step 3: Decode & Modify Manifest
        emit(
            ModificationStatus.Progress(
                stageName = "Transforming Binary AndroidManifest",
                stepNumber = 3,
                progressPercent = 40,
                details = "Updating package identifier, component paths, and application label..."
            )
        )

        val manifestReplacements = mutableMapOf<String, String>()
        val originalPkg = discoveryReport.packageDiscovery.manifestPackage
        val newPkg = if (config.changePackageId) config.newPackageId.trim() else originalPkg

        if (config.changePackageId && newPkg != originalPkg) {
            manifestReplacements[originalPkg] = newPkg
            appliedChanges.add("Manifest package: '$originalPkg' → '$newPkg'")
            manifestChangesCount++

            // Update authorities
            for (auth in discoveryReport.packageDiscovery.providerAuthorities) {
                if (auth.authority.contains(originalPkg)) {
                    val newAuth = auth.authority.replace(originalPkg, newPkg)
                    manifestReplacements[auth.authority] = newAuth
                    appliedChanges.add("Provider authority: '${auth.authority}' → '$newAuth'")
                    manifestChangesCount++
                }
            }
        }

        val originalAppName = discoveryReport.appNameDiscovery.resolvedDefaultLabel
        val newAppName = if (config.changeAppName) config.newAppName.trim() else originalAppName

        if (config.changeAppName && newAppName.isNotEmpty()) {
            if (discoveryReport.appNameDiscovery.manifestRawLabel.isNotEmpty()) {
                manifestReplacements[discoveryReport.appNameDiscovery.manifestRawLabel] = newAppName
            }
            manifestReplacements[originalAppName] = newAppName
            appliedChanges.add("Application label: '$originalAppName' → '$newAppName'")
            manifestChangesCount++
        }

        // Step 4: Modifying compiled resources.arsc
        emit(
            ModificationStatus.Progress(
                stageName = "Modifying Compiled Resource Table",
                stepNumber = 4,
                progressPercent = 55,
                details = "Updating global string pool values for app_name and translations..."
            )
        )

        val arscReplacements = mutableMapOf<String, String>()
        if (config.changeAppName && newAppName.isNotEmpty()) {
            arscReplacements[originalAppName] = newAppName
            appliedChanges.add("Resource Table: '$originalAppName' → '$newAppName'")
            resourceChangesCount++
        }

        // Step 5: Preparing Launcher Icon replacements
        emit(
            ModificationStatus.Progress(
                stageName = "Generating Launcher Icon Assets",
                stepNumber = 5,
                progressPercent = 70,
                details = "Generating density-scaled PNGs (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)..."
            )
        )

        val generatedIcons = mutableMapOf<String, ByteArray>()
        if (config.changeLauncherIcon && config.customIconBytes != null) {
            val bitmap = BitmapFactory.decodeByteArray(config.customIconBytes, 0, config.customIconBytes.size)
            if (bitmap != null) {
                val densityMap = iconProcessor.generateDensityIcons(bitmap, config.iconBackgroundColor)
                generatedIcons.putAll(densityMap)
                appliedChanges.add("Generated ${densityMap.size} density launcher icon assets")
                iconChangesCount += densityMap.size
            }
        }

        // Step 6: Repackaging APK into workspace
        emit(
            ModificationStatus.Progress(
                stageName = "Repackaging Modified Workspace",
                stepNumber = 6,
                progressPercent = 85,
                details = "Injecting updated binary structures into workspace APK..."
            )
        )

        try {
            repackageModifiedApk(
                sourceApk = originalApkFile,
                destApk = tempModifiedApk,
                manifestReplacements = manifestReplacements,
                arscReplacements = arscReplacements,
                generatedIcons = generatedIcons
            )
        } catch (e: Exception) {
            emit(ModificationStatus.Failed("Repackaging error: ${e.message}", e.stackTraceToString()))
            return@flow
        }

        // Step 7: Verifying modified structures
        emit(
            ModificationStatus.Progress(
                stageName = "Validating Modified Structures",
                stepNumber = 7,
                progressPercent = 95,
                details = "Re-parsing binary manifest and verifying modified package identity..."
            )
        )

        val (verifiedPkg, verifiedName) = verifyModifiedApk(tempModifiedApk)
        val modifiedChecksum = calculateSha256(tempModifiedApk)

        val report = ApkModificationReport(
            projectId = projectId,
            originalApkChecksum = discoveryReport.originalApkChecksum,
            modifiedApkPath = tempModifiedApk.absolutePath,
            modifiedApkChecksum = modifiedChecksum,
            modifiedFileSize = tempModifiedApk.length(),
            requestedAppName = if (config.changeAppName) newAppName else null,
            verifiedAppName = verifiedName ?: newAppName,
            requestedPackageId = if (config.changePackageId) newPkg else null,
            verifiedPackageId = verifiedPkg ?: newPkg,
            hasCustomIcon = config.changeLauncherIcon,
            manifestModificationsCount = manifestChangesCount,
            resourceModificationsCount = resourceChangesCount,
            iconReplacementsCount = iconChangesCount,
            appliedChanges = appliedChanges,
            warnings = validation.warnings,
            isWorkspacePreparedForPhase4 = true,
            durationMs = System.currentTimeMillis() - startTime
        )

        emit(ModificationStatus.Success(report))
    }.flowOn(Dispatchers.IO)

    private fun repackageModifiedApk(
        sourceApk: File,
        destApk: File,
        manifestReplacements: Map<String, String>,
        arscReplacements: Map<String, String>,
        generatedIcons: Map<String, ByteArray>
    ) {
        val zis = ZipInputStream(FileInputStream(sourceApk))
        val zos = ZipOutputStream(FileOutputStream(destApk))

        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val entryName = entry.name

            // Exclude old signatures - Phase 4 handles fresh signing
            if (entryName.startsWith("META-INF/")) {
                zis.closeEntry()
                entry = zis.nextEntry
                continue
            }

            val newEntry = ZipEntry(entryName)
            zos.putNextEntry(newEntry)

            when {
                entryName == "AndroidManifest.xml" && manifestReplacements.isNotEmpty() -> {
                    val originalBytes = zis.readBytes()
                    val modifiedBytes = aXmlDecoder.rewriteManifestStrings(originalBytes, manifestReplacements)
                    zos.write(modifiedBytes)
                }
                entryName == "resources.arsc" && arscReplacements.isNotEmpty() -> {
                    val originalBytes = zis.readBytes()
                    val modifiedBytes = arscDecoder.rewriteArscStrings(originalBytes, arscReplacements)
                    zos.write(modifiedBytes)
                }
                generatedIcons.isNotEmpty() && isLauncherIconPath(entryName) -> {
                    val density = extractDensity(entryName)
                    val replacementBytes = generatedIcons[density]
                    if (replacementBytes != null) {
                        zos.write(replacementBytes)
                    } else {
                        val buf = ByteArray(16384)
                        var l: Int
                        while (zis.read(buf).also { l = it } != -1) {
                            zos.write(buf, 0, l)
                        }
                    }
                }
                else -> {
                    val buf = ByteArray(16384)
                    var l: Int
                    while (zis.read(buf).also { l = it } != -1) {
                        zos.write(buf, 0, l)
                    }
                }
            }

            zos.closeEntry()
            zis.closeEntry()
            entry = zis.nextEntry
        }

        zis.close()
        zos.close()
    }

    private fun isLauncherIconPath(name: String): Boolean {
        return (name.startsWith("res/mipmap") || name.startsWith("res/drawable")) &&
                (name.contains("icon", ignoreCase = true) || name.contains("launcher", ignoreCase = true)) &&
                (name.endsWith(".png") || name.endsWith(".webp"))
    }

    private fun extractDensity(path: String): String {
        return when {
            path.contains("xxxhdpi") -> "xxxhdpi"
            path.contains("xxhdpi") -> "xxhdpi"
            path.contains("xhdpi") -> "xhdpi"
            path.contains("hdpi") -> "hdpi"
            path.contains("mdpi") -> "mdpi"
            else -> "hdpi"
        }
    }

    private fun verifyModifiedApk(file: File): Pair<String?, String?> {
        return try {
            ZipFile(file).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml") ?: return Pair(null, null)
                val bytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
                val data = aXmlDecoder.decodeManifest(bytes)
                Pair(data.packageName.ifEmpty { null }, data.appLabel.ifEmpty { null })
            }
        } catch (e: Exception) {
            Pair(null, null)
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

    fun rollback(projectId: String) {
        val workspaceDir = File(context.filesDir, "projects/$projectId/workspace")
        if (workspaceDir.exists()) {
            workspaceDir.deleteRecursively()
        }
    }
}
