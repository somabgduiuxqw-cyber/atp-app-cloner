package com.atp.cloner.engine.build

data class ApkBuildReport(
    val projectId: String,
    val originalApkName: String,
    val originalApkChecksum: String,
    val rebuiltApkName: String,
    val rebuiltApkPath: String,
    val rebuiltApkChecksum: String,
    val rebuiltFileSize: Long,
    val targetPackageId: String,
    val verifiedPackageId: String,
    val targetAppName: String,
    val verifiedAppName: String,
    val hasCustomIcon: Boolean,
    val alignmentSuccess: Boolean,
    val alignedEntriesCount: Int,
    val signingCertFingerprint: String,
    val signingAlgorithm: String = "RSA-2048 with SHA-256 (v1 Scheme)",
    val signatureVerified: Boolean,
    val dexFilesCount: Int,
    val dexChecksumValid: Boolean,
    val isInstallReady: Boolean,
    val stageTimings: Map<String, Long> = emptyMap(),
    val buildLogs: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val totalDurationMs: Long = 0L
) {
    val formattedFileSize: String
        get() = when {
            rebuiltFileSize < 1024 -> "$rebuiltFileSize B"
            rebuiltFileSize < 1024 * 1024 -> String.format("%.2f KB", rebuiltFileSize / 1024.0)
            else -> String.format("%.2f MB", rebuiltFileSize / (1024.0 * 1024.0))
        }
}
