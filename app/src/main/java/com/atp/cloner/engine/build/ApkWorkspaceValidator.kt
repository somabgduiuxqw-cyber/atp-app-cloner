package com.atp.cloner.engine.build

import java.io.File
import java.util.zip.ZipFile

data class WorkspaceValidationResult(
    val isValid: Boolean,
    val workingApk: File? = null,
    val hasManifest: Boolean = false,
    val hasResourcesArsc: Boolean = false,
    val dexFilesCount: Int = 0,
    val errorMessage: String? = null
)

class ApkWorkspaceValidator {

    fun validateWorkspace(workspaceDir: File): WorkspaceValidationResult {
        if (!workspaceDir.exists() || !workspaceDir.isDirectory) {
            return WorkspaceValidationResult(
                isValid = false,
                errorMessage = "Workspace directory does not exist: ${workspaceDir.path}"
            )
        }

        val workingApk = File(workspaceDir, "working_modified.apk")
        if (!workingApk.exists() || workingApk.length() == 0L) {
            return WorkspaceValidationResult(
                isValid = false,
                errorMessage = "Prepared working_modified.apk not found in workspace. Run Phase 3 modification first."
            )
        }

        return try {
            var hasManifest = false
            var hasResources = false
            var dexCount = 0

            ZipFile(workingApk).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name == "AndroidManifest.xml") hasManifest = true
                    if (entry.name == "resources.arsc") hasResources = true
                    if (entry.name.endsWith(".dex")) dexCount++
                }
            }

            if (!hasManifest) {
                return WorkspaceValidationResult(
                    isValid = false,
                    workingApk = workingApk,
                    errorMessage = "Working APK is missing AndroidManifest.xml"
                )
            }

            WorkspaceValidationResult(
                isValid = true,
                workingApk = workingApk,
                hasManifest = hasManifest,
                hasResourcesArsc = hasResources,
                dexFilesCount = dexCount
            )
        } catch (e: Exception) {
            WorkspaceValidationResult(
                isValid = false,
                workingApk = workingApk,
                errorMessage = "Invalid APK archive in workspace: ${e.message}"
            )
        }
    }
}
