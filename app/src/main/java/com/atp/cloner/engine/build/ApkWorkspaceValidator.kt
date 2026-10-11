package com.atp.cloner.engine.build

import java.io.File
import java.util.zip.ZipFile

data class WorkspaceValidationResult(
    val isValid: Boolean,
    val modifiedApkFile: File?,
    val hasManifest: Boolean,
    val hasResources: Boolean,
    val hasDex: Boolean,
    val dexCount: Int,
    val nativeLibCount: Int,
    val missingFiles: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val details: String = ""
)

class ApkWorkspaceValidator {

    /**
     * Validates that the workspace contains the prepared modified APK or assets
     * from Phase 3 modification engine before proceeding with rebuilding.
     */
    fun validateWorkspace(workspaceDir: File): WorkspaceValidationResult {
        if (!workspaceDir.exists() || !workspaceDir.isDirectory) {
            return WorkspaceValidationResult(
                isValid = false,
                modifiedApkFile = null,
                hasManifest = false,
                hasResources = false,
                hasDex = false,
                dexCount = 0,
                nativeLibCount = 0,
                missingFiles = listOf("Workspace directory: ${workspaceDir.absolutePath}"),
                details = "Workspace directory does not exist or has not been initialized."
            )
        }

        val workingModifiedApk = File(workspaceDir, "working_modified.apk")
        if (!workingModifiedApk.exists() || workingModifiedApk.length() == 0L) {
            return WorkspaceValidationResult(
                isValid = false,
                modifiedApkFile = null,
                hasManifest = false,
                hasResources = false,
                hasDex = false,
                dexCount = 0,
                nativeLibCount = 0,
                missingFiles = listOf("working_modified.apk"),
                details = "Missing working_modified.apk in workspace. Phase 3 modification must be completed first."
            )
        }

        var hasManifest = false
        var hasResources = false
        var dexCount = 0
        var nativeLibCount = 0
        val warnings = mutableListOf<String>()

        try {
            ZipFile(workingModifiedApk).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (name == "AndroidManifest.xml") {
                        hasManifest = true
                    }
                    if (name == "resources.arsc") {
                        hasResources = true
                    }
                    if (name.startsWith("classes") && name.endsWith(".dex")) {
                        dexCount++
                    }
                    if (name.startsWith("lib/") && name.endsWith(".so")) {
                        nativeLibCount++
                    }
                }
            }
        } catch (e: Exception) {
            return WorkspaceValidationResult(
                isValid = false,
                modifiedApkFile = workingModifiedApk,
                hasManifest = false,
                hasResources = false,
                hasDex = false,
                dexCount = 0,
                nativeLibCount = 0,
                missingFiles = listOf("Corrupt ZIP archive"),
                details = "Failed to open working modified APK: ${e.message}"
            )
        }

        val missing = mutableListOf<String>()
        if (!hasManifest) missing.add("AndroidManifest.xml")
        if (dexCount == 0) missing.add("classes.dex")

        if (!hasResources) {
            warnings.add("resources.arsc not found (APK might use resource-less configuration).")
        }

        val isValid = missing.isEmpty()
        val details = if (isValid) {
            "Workspace is valid: Manifest present, $dexCount DEX file(s), $nativeLibCount native library(ies)."
        } else {
            "Workspace missing critical components: ${missing.joinToString(", ")}"
        }

        return WorkspaceValidationResult(
            isValid = isValid,
            modifiedApkFile = workingModifiedApk,
            hasManifest = hasManifest,
            hasResources = hasResources,
            hasDex = dexCount > 0,
            dexCount = dexCount,
            nativeLibCount = nativeLibCount,
            missingFiles = missing,
            warnings = warnings,
            details = details
        )
    }
}
