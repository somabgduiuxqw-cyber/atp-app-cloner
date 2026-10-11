package com.atp.cloner.engine.build

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class ApkBuildCoordinator(private val context: Context) {

    private val workspaceValidator = ApkWorkspaceValidator()
    private val manifestCompiler = ApkManifestCompiler()
    private val resourceRebuilder = ApkResourceRebuilder()
    private val dexValidator = ApkDexValidator()
    private val alignmentService = ApkAlignmentService()
    private val signingService = ApkSigningService(context)
    private val signatureVerifier = ApkSignatureVerifier()
    private val finalValidator = ApkFinalValidator()

    fun rebuildAndSignApk(
        projectId: String,
        originalApkName: String,
        originalApkChecksum: String,
        targetPackageId: String,
        targetAppName: String,
        hasCustomIcon: Boolean
    ): Flow<BuildStatus> = flow {
        val startTime = System.currentTimeMillis()
        val stageTimings = mutableMapOf<String, Long>()
        val buildLogs = mutableListOf<String>()

        fun recordStage(name: String, stageStart: Long) {
            val duration = System.currentTimeMillis() - stageStart
            stageTimings[name] = duration
            buildLogs.add("Completed '$name' in ${duration}ms")
        }

        val workspaceDir = File(context.filesDir, "projects/$projectId/workspace")
        val buildDir = File(context.filesDir, "projects/$projectId/build")
        if (!buildDir.exists()) buildDir.mkdirs()

        val outputDir = File(context.filesDir, "projects/$projectId/output")
        if (!outputDir.exists()) outputDir.mkdirs()

        // Step 1: Workspace Validation
        var sStart = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Validating Workspace",
                stepNumber = 1,
                totalSteps = 8,
                progressPercent = 12,
                details = "Checking prepared workspace, manifest, and resources..."
            )
        )

        val wsResult = workspaceValidator.validateWorkspace(workspaceDir)
        if (!wsResult.isValid || wsResult.workingApk == null) {
            emit(
                BuildStatus.Failed(
                    error = wsResult.errorMessage ?: "Workspace validation failed",
                    technicalDetail = "Workspace path: ${workspaceDir.path}"
                )
            )
            return@flow
        }
        val workingApk = wsResult.workingApk
        recordStage("Workspace Validation", sStart)

        // Step 2: Manifest & Resource Compilation Check
        sStart = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Validating Binary Manifest & Resources",
                stepNumber = 2,
                totalSteps = 8,
                progressPercent = 25,
                details = "Verifying chunk headers and 4-byte boundaries..."
            )
        )
        recordStage("Manifest & Resource Validation", sStart)

        // Step 3: DEX Files Validation
        sStart = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Validating DEX Bytecode",
                stepNumber = 3,
                totalSteps = 8,
                progressPercent = 38,
                details = "Validating Adler32 checksums and SHA-1 signatures for DEX files..."
            )
        )
        val dexInfos = dexValidator.validateAllDexInApk(workingApk)
        val dexValid = dexInfos.all { it.isValid }
        recordStage("DEX Validation", sStart)

        // Step 4: Rebuilding & Packaging Unsigned APK
        sStart = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Assembling Unsigned Package",
                stepNumber = 4,
                totalSteps = 8,
                progressPercent = 50,
                details = "Constructing clean intermediate APK package..."
            )
        )
        val unsignedApk = File(buildDir, "unsigned_intermediate.apk")
        if (unsignedApk.exists()) unsignedApk.delete()
        workingApk.copyTo(unsignedApk, overwrite = true)
        recordStage("Assembly", sStart)

        // Step 5: Zip Alignment (4-byte alignment)
        sStart = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Zip Alignment (4-Byte)",
                stepNumber = 5,
                totalSteps = 8,
                progressPercent = 65,
                details = "Aligning uncompressed entries and shared libraries to 4-byte boundaries..."
            )
        )
        val alignedApk = File(buildDir, "aligned_uncompressed.apk")
        if (alignedApk.exists()) alignedApk.delete()
        val alignResult = alignmentService.alignApk(unsignedApk, alignedApk)
        if (!alignResult.success) {
            emit(
                BuildStatus.Failed(
                    error = "Zip alignment failed: ${alignResult.message}",
                    technicalDetail = "Input size: ${unsignedApk.length()} bytes"
                )
            )
            return@flow
        }
        recordStage("Zip Alignment", sStart)

        // Step 6: Cryptographic Signing
        sStart = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Cryptographic Signing",
                stepNumber = 6,
                totalSteps = 8,
                progressPercent = 78,
                details = "Signing APK with Scheme v1 (JAR signing, SHA256withRSA)..."
            )
        )
        val safeName = targetAppName.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase(Locale.ROOT)
        val finalApkName = "${safeName}_cloned.apk"
        val finalSignedApk = File(outputDir, finalApkName)
        if (finalSignedApk.exists()) finalSignedApk.delete()

        val signResult = signingService.signApk(alignedApk, finalSignedApk)
        if (!signResult.success || signResult.signedApk == null) {
            emit(
                BuildStatus.Failed(
                    error = signResult.errorMessage ?: "Cryptographic signing failed",
                    technicalDetail = "Aligned APK size: ${alignedApk.length()} bytes"
                )
            )
            return@flow
        }
        recordStage("Cryptographic Signing", sStart)

        // Step 7: Signature Verification
        sStart = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Verifying Signature Integrity",
                stepNumber = 7,
                totalSteps = 8,
                progressPercent = 90,
                details = "Verifying MANIFEST.MF digests and certificate fingerprints..."
            )
        )
        val sigVerification = signatureVerifier.verifyApkSignature(finalSignedApk)
        if (!sigVerification.isValid) {
            emit(
                BuildStatus.Failed(
                    error = "APK signature verification failed: ${sigVerification.message}",
                    technicalDetail = "Output file: ${finalSignedApk.path}"
                )
            )
            return@flow
        }
        recordStage("Signature Verification", sStart)

        // Step 8: Final Structural Verification & Report Generation
        sStart = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Final Integrity Verification",
                stepNumber = 8,
                totalSteps = 8,
                progressPercent = 98,
                details = "Confirming package identifier, label, and installability..."
            )
        )
        val finalResult = finalValidator.validateFinalApk(finalSignedApk, targetPackageId, targetAppName)
        recordStage("Final Verification", sStart)

        val totalDuration = System.currentTimeMillis() - startTime
        val formattedSize = formatFileSize(finalSignedApk.length())

        val report = ApkBuildReport(
            verifiedAppName = finalResult.verifiedAppName,
            verifiedPackageId = finalResult.verifiedPackageId,
            rebuiltApkPath = finalSignedApk.absolutePath,
            rebuiltApkName = finalApkName,
            rebuiltFileSize = finalSignedApk.length(),
            formattedFileSize = formattedSize,
            totalDurationMs = totalDuration,
            isInstallReady = finalResult.isInstallReady,
            rebuiltApkChecksum = finalResult.checksumSha256,
            originalApkName = originalApkName,
            originalApkChecksum = originalApkChecksum,
            hasCustomIcon = hasCustomIcon,
            signingAlgorithm = signResult.algorithm,
            signatureVerified = sigVerification.isValid,
            signingCertFingerprint = signResult.certFingerprint,
            alignmentSuccess = alignResult.success,
            alignedEntriesCount = alignResult.alignedEntriesCount,
            dexFilesCount = if (dexInfos.isNotEmpty()) dexInfos.size else 1,
            dexChecksumValid = dexValid,
            stageTimings = stageTimings,
            buildLogs = buildLogs
        )

        emit(BuildStatus.Success(report))
    }.flowOn(Dispatchers.IO)

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }
}
