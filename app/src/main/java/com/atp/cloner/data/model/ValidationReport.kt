package com.atp.cloner.data.model

data class ValidationReport(
    val outputFileName: String,
    val outputFilePath: String,
    val outputFileSize: Long,
    val outputSha256: String,
    val originalSha256: String,
    val verifiedPackageName: String,
    val verifiedAppLabel: String,
    val hasCustomIcon: Boolean,
    val isSignatureVerified: Boolean,
    val signatureCertFingerprint: String,
    val isZipAligned: Boolean,
    val isManifestReadable: Boolean,
    val logs: List<String>,
    val durationMs: Long
)
