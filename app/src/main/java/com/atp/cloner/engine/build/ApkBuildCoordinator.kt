package com.atp.cloner.engine.build

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

class ApkBuildCoordinator(private val context: Context) {

    private val alignmentService = ApkAlignmentService()
    private val signingService = ApkSigningService(context)
    private val dexValidator = ApkDexValidator()
    private val signatureVerifier = ApkSignatureVerifier()
    private val finalValidator = ApkFinalValidator(context)

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
        val logs = mutableListOf<String>()

        fun log(msg: String) {
            logs.add("[${System.currentTimeMillis() - startTime}ms] $msg")
        }

        val projectDir = File(context.filesDir, "projects/$projectId")
        val stagingApk = File(projectDir, "staging.apk")
        val originalApk = File(projectDir, "original.apk")

        // Input source file for rebuild pipeline
        val sourceApk = if (stagingApk.exists() && stagingApk.length() > 0) stagingApk else originalApk

        if (!sourceApk.exists() || sourceApk.length() == 0L) {
            emit(BuildStatus.Failed("Workspace source APK not found for project: $projectId"))
            return@flow
        }

        // Stage 1: Workspace & DEX inspection
        val stage1Start = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Validating Workspace & DEX",
                stepNumber = 1,
                totalSteps = 6,
                progressPercent = 15,
                details = "Analyzing bytecode structures, classes.dex references, and assets..."
            )
        )
        val dexResult = dexValidator.validateDex(sourceApk)
        stageTimings["DEX Validation"] = System.currentTimeMillis() - stage1Start
        log("Validated DEX files: ${dexResult.dexCount} files discovered. ${dexResult.details}")

        // Stage 2: Alignment preparation
        val stage2Start = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Optimizing Archive & Zip Alignment",
                stepNumber = 2,
                totalSteps = 6,
                progressPercent = 35,
                details = "Aligning uncompressed 4-byte boundaries for memory-mapped execution..."
            )
        )
        val alignedFile = File(projectDir, "aligned.apk")
        val alignResult = alignmentService.alignApk(sourceApk, alignedFile)
        stageTimings["Zip Alignment"] = System.currentTimeMillis() - stage2Start

        if (!alignResult.success) {
            emit(BuildStatus.Failed("Zipalign operation failed", alignResult.errorMessage))
            return@flow
        }
        log("Zipalign succeeded: ${alignResult.alignedEntriesCount} entries processed.")

        // Stage 3: Signing with v1 Scheme
        val stage3Start = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Cryptographic Signing",
                stepNumber = 3,
                totalSteps = 6,
                progressPercent = 60,
                details = "Generating SHA-256 digests and injecting META-INF signature blocks..."
            )
        )
        val outputFile = File(projectDir, "cloned_${projectId.take(8)}.apk")
        val signResult = signingService.signApk(alignedFile, outputFile)
        stageTimings["APK Signing"] = System.currentTimeMillis() - stage3Start

        if (!signResult.success || signResult.signedApkFile == null) {
            emit(BuildStatus.Failed("Signing failed", signResult.errorMessage))
            return@flow
        }
        log("APK signed using ${signResult.algorithm}. Cert fingerprint: ${signResult.certFingerprint}")

        // Stage 4: Signature Verification
        val stage4Start = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Signature Verification",
                stepNumber = 4,
                totalSteps = 6,
                progressPercent = 80,
                details = "Independently verifying JAR manifest digests and cryptographic hashes..."
            )
        )
        val sigVerification = signatureVerifier.verifyApkSignature(outputFile)
        stageTimings["Signature Verification"] = System.currentTimeMillis() - stage4Start
        log("Signature verification status: ${sigVerification.isSigned}")

        // Stage 5: Final Output Inspection
        val stage5Start = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Final Integrity Inspection",
                stepNumber = 5,
                totalSteps = 6,
                progressPercent = 95,
                details = "Reading final manifest and computing SHA-256 checksum..."
            )
        )
        val finalResult = finalValidator.validateFinalApk(outputFile, targetPackageId, targetAppName)
        stageTimings["Final Inspection"] = System.currentTimeMillis() - stage5Start
        log("Output APK validated. SHA-256: ${finalResult.sha256Checksum}")

        // Stage 6: Build Success & Complete Report
        val totalDuration = System.currentTimeMillis() - startTime
        val formattedSize = formatFileSize(outputFile.length())

        val report = ApkBuildReport(
            rebuiltApkPath = outputFile.absolutePath,
            rebuiltApkName = outputFile.name,
            rebuiltApkChecksum = finalResult.sha256Checksum,
            rebuiltFileSize = outputFile.length(),
            formattedFileSize = formattedSize,
            originalApkName = originalApkName,
            originalApkChecksum = originalApkChecksum,
            verifiedAppName = finalResult.verifiedAppName,
            verifiedPackageId = finalResult.verifiedPackageId,
            hasCustomIcon = hasCustomIcon,
            signingAlgorithm = signResult.algorithm,
            signatureVerified = sigVerification.isSigned,
            signingCertFingerprint = signResult.certFingerprint.ifEmpty { sigVerification.certFingerprintSha256 },
            alignmentSuccess = alignResult.success,
            alignedEntriesCount = alignResult.alignedEntriesCount,
            dexFilesCount = dexResult.dexCount,
            dexChecksumValid = dexResult.valid,
            isInstallReady = finalResult.success && sigVerification.isSigned,
            totalDurationMs = totalDuration,
            stageTimings = stageTimings,
            buildLogs = logs
        )

        emit(BuildStatus.Success(report))
    }.flowOn(Dispatchers.IO)

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
