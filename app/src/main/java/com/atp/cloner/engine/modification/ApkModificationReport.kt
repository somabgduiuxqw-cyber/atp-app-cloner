package com.atp.cloner.engine.modification

data class ApkModificationReport(
    val projectId: String,
    val originalApkChecksum: String,
    val modifiedApkPath: String,
    val modifiedApkChecksum: String,
    val modifiedFileSize: Long,
    val requestedAppName: String?,
    val verifiedAppName: String?,
    val requestedPackageId: String?,
    val verifiedPackageId: String?,
    val hasCustomIcon: Boolean,
    val manifestModificationsCount: Int,
    val resourceModificationsCount: Int,
    val iconReplacementsCount: Int,
    val appliedChanges: List<String>,
    val warnings: List<String>,
    val isWorkspacePreparedForPhase4: Boolean,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
