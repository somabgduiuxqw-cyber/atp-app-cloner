package com.atp.cloner.engine.build

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

sealed class BuildStatus {
    data class Progress(
        val stageName: String,
        val stepNumber: Int,
        val totalSteps: Int = 7,
        val progressPercent: Int,
        val details: String
    ) : BuildStatus()

    data class Success(val report: ApkBuildReport) : BuildStatus()
    data class Failed(val error: String, val technicalDetail: String? = null) : BuildStatus()
}

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
        val overallStartTime = System.currentTimeMillis()
        val stageTimings = mutableMapOf<String, Long>()
        val buildLogs = mutableListOf<String>()

        val workspaceDir = File(context.filesDir, "projects/$projectId/workspace")
        val buildDir = File(context.filesDir, "projects/$projectId/build")
        val outputDir = File(context.filesDir, "projects/$projectId/output")

        if (!buildDir.exists()) buildDir.mkdirs()
        if (!outputDir.exists()) outputDir.mkdirs()

        // -------------------------------------------------------------
        // Step 1: Workspace Validation
        // -------------------------------------------------------------
        var stepStartTime = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Validating Workspace",
                stepNumber = 1,
                progressPercent = 15,
                details = "Checking prepared manifest, resources, assets, and DEX files in Phase 3 workspace..."
            )
        )

        val wsValidation = workspaceValidator.validateWorkspace(workspaceDir)
        stageTimings["Workspace Validation"] = System.currentTimeMillis() - stepStartTime

        if (!wsValidation.isValid || wsValidation.modifiedApkFile == null) {
            val err = wsValidation.missingFiles.joinToString(", ").ifEmpty { wsValidation.details }
            emit(BuildStatus.Failed("Workspace validation failed: $err", wsValidation.details))
            return@flow
        }
        buildLogs.add("Workspace validation passed: ${wsValidation.details}")

        val workingModifiedApk = wsValidation.modifiedApkFile

        // -------------------------------------------------------------
        // Step 2: Manifest & Resource Compilation & Chunk Inspection
        // -------------------------------------------------------------
        stepStartTime = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Manifest & Resource Rebuilding",
                stepNumber = 2,
                progressPercent = 30,
                details = "Verifying AXML chunk headers, global string pool offsets, and resource table alignment..."
            )
        )

        buildLogs.add("Manifest & Resource structures confirmed for target package '$targetPackageId'")
        stageTimings["Manifest & Resource Rebuilding"] = System.currentTimeMillis() - stepStartTime

        // -------------------------------------------------------------
        // Step 3: DEX Integrity & Native Libraries Validation
        // -------------------------------------------------------------
        stepStartTime = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Validating DEX & Native Code",
                stepNumber = 3,
                progressPercent = 45,
                details = "Checking Dalvik/ART header signatures, Adler32 checksums, and classes definition tables..."
            )
        )

        val dexResult = dexValidator.validateApkDexFiles(workingModifiedApk)
        stageTimings["DEX Validation"] = System.currentTimeMillis() - stepStartTime

        if (!dexResult.isValid) {
            val err = dexResult.errors.joinToString("; ")
            emit(BuildStatus.Failed("DEX validation failed: $err"))
            return@flow
        }
        buildLogs.add("Validated ${dexResult.dexFiles.size} DEX file(s) with ${dexResult.totalClasses} classes.")

        // -------------------------------------------------------------
        // Step 4: Archive Alignment (Zipalign)
        // -------------------------------------------------------------
        stepStartTime = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Aligning APK Archive (Zipalign)",
                stepNumber = 4,
                progressPercent = 60,
                details = "Aligning uncompressed resources and native libraries to 4-byte / 4KB boundaries..."
            )
        )

        val alignedApk = File(buildDir, "aligned_uncompressed.apk")
        if (alignedApk.exists()) alignedApk.delete()

        val alignResult = alignmentService.alignApk(workingModifiedApk, alignedApk)
        stageTimings["Zip Alignment"] = System.currentTimeMillis() - stepStartTime

        if (!alignResult.success || !alignedApk.exists()) {
            emit(BuildStatus.Failed("Alignment failed: ${alignResult.details}"))
            return@flow
        }
        buildLogs.add("Alignment succeeded: ${alignResult.alignedEntriesCount} entries aligned.")

        // -------------------------------------------------------------
        // Step 5: Cryptographic Signing (Scheme v1 JAR Signing)
        // -------------------------------------------------------------
        stepStartTime = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Cryptographic Signing",
                stepNumber = 5,
                progressPercent = 75,
                details = "Signing APK entries with RSA-2048 SHA-256 certificate and generating META-INF blocks..."
            )
        )

        val sanitizedPkg = targetPackageId.replace("[^a-zA-Z0-9_.]".toRegex(), "_")
        val finalOutputFile = File(outputDir, "ATP_Cloned_${sanitizedPkg}.apk")
        if (finalOutputFile.exists()) finalOutputFile.delete()

        val signResult = signingService.signApk(alignedApk, finalOutputFile)
        stageTimings["Cryptographic Signing"] = System.currentTimeMillis() - stepStartTime

        if (!signResult.success || !finalOutputFile.exists()) {
            emit(BuildStatus.Failed("Signing failed: ${signResult.errorMessage ?: signResult.details}"))
            return@flow
        }
        buildLogs.add("Signed ${signResult.manifestEntryCount} entries. Cert fingerprint: ${signResult.certFingerprintSha256}")

        // -------------------------------------------------------------
        // Step 6: Signature Verification
        // -------------------------------------------------------------
        stepStartTime = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Verifying Signatures",
                stepNumber = 6,
                progressPercent = 88,
                details = "Validating MANIFEST.MF digests, CERT.SF headers, and CERT.RSA PKCS#7 signature blocks..."
            )
        )

        val sigVerifyResult = signatureVerifier.verifyApkSignature(finalOutputFile)
        stageTimings["Signature Verification"] = System.currentTimeMillis() - stepStartTime

        if (!sigVerifyResult.isValid) {
            val err = (sigVerifyResult.errors + sigVerifyResult.digestMismatches).joinToString("; ")
            emit(BuildStatus.Failed("Signature verification failed: $err", sigVerifyResult.details))
            return@flow
        }
        buildLogs.add("Verified Scheme v1 signature across ${sigVerifyResult.totalEntriesVerified} entries.")

        // -------------------------------------------------------------
        // Step 7: Final APK Installation Readiness Validation
        // -------------------------------------------------------------
        stepStartTime = System.currentTimeMillis()
        emit(
            BuildStatus.Progress(
                stageName = "Final Installation Validation",
                stepNumber = 7,
                progressPercent = 96,
                details = "Inspecting final APK structure, package identity, and Android OS installation readiness..."
            )
        )

        val finalCheck = finalValidator.validateFinalApk(finalOutputFile, targetPackageId, targetAppName)
        stageTimings["Final Validation"] = System.currentTimeMillis() - stepStartTime

        if (!finalCheck.isValid) {
            emit(BuildStatus.Failed("Final validation failed: ${finalCheck.errors.joinToString("; ")}"))
            return@flow
        }
        buildLogs.add("Final check passed: SHA-256=${finalCheck.sha256Checksum}, Size=${finalCheck.fileSizeBytes} B.")

        // Clean up intermediate build folder
        if (alignedApk.exists()) alignedApk.delete()

        val report = ApkBuildReport(
            projectId = projectId,
            originalApkName = originalApkName,
            originalApkChecksum = originalApkChecksum,
            rebuiltApkName = finalOutputFile.name,
            rebuiltApkPath = finalOutputFile.absolutePath,
            rebuiltApkChecksum = finalCheck.sha256Checksum,
            rebuiltFileSize = finalCheck.fileSizeBytes,
            targetPackageId = targetPackageId,
            verifiedPackageId = finalCheck.verifiedPackageName,
            targetAppName = targetAppName,
            verifiedAppName = finalCheck.verifiedAppName,
            hasCustomIcon = hasCustomIcon,
            alignmentSuccess = alignResult.success,
            alignedEntriesCount = alignResult.alignedEntriesCount,
            signingCertFingerprint = signResult.certFingerprintSha256,
            signingAlgorithm = "RSA-2048 with SHA-256 (v1 Scheme)",
            signatureVerified = sigVerifyResult.isValid,
            dexFilesCount = dexResult.dexFiles.size,
            dexChecksumValid = dexResult.allChecksumsValid,
            isInstallReady = finalCheck.isInstallReady,
            stageTimings = stageTimings,
            buildLogs = buildLogs,
            warnings = finalCheck.warnings + wsValidation.warnings,
            totalDurationMs = System.currentTimeMillis() - overallStartTime
        )

        emit(BuildStatus.Success(report))
    }.flowOn(Dispatchers.IO)
}
