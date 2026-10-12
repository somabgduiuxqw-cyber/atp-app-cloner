package com.atp.cloner.engine.build

data class ApkBuildReport(
    val rebuiltApkPath: String,
    val rebuiltApkName: String,
    val rebuiltApkChecksum: String,
    val rebuiltFileSize: Long,
    val formattedFileSize: String,
    val originalApkName: String,
    val originalApkChecksum: String,
    val verifiedAppName: String,
    val verifiedPackageId: String,
    val hasCustomIcon: Boolean,
    val signingAlgorithm: String,
    val signatureVerified: Boolean,
    val signingCertFingerprint: String,
    val alignmentSuccess: Boolean,
    val alignedEntriesCount: Int,
    val dexFilesCount: Int,
    val dexChecksumValid: Boolean,
    val isInstallReady: Boolean,
    val totalDurationMs: Long,
    val stageTimings: Map<String, Long>,
    val buildLogs: List<String>
)
