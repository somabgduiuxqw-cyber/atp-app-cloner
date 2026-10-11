package com.atp.cloner.engine.build

data class ApkBuildReport(
    val verifiedAppName: String,
    val verifiedPackageId: String,
    val rebuiltApkPath: String,
    val rebuiltApkName: String,
    val rebuiltFileSize: Long,
    val formattedFileSize: String,
    val totalDurationMs: Long,
    val isInstallReady: Boolean,
    val rebuiltApkChecksum: String,
    val originalApkName: String,
    val originalApkChecksum: String,
    val hasCustomIcon: Boolean,
    val signingAlgorithm: String = "SHA256withRSA",
    val signatureVerified: Boolean = true,
    val signingCertFingerprint: String = "",
    val alignmentSuccess: Boolean = true,
    val alignedEntriesCount: Int = 0,
    val dexFilesCount: Int = 1,
    val dexChecksumValid: Boolean = true,
    val stageTimings: Map<String, Long> = emptyMap(),
    val buildLogs: List<String> = emptyList()
)
