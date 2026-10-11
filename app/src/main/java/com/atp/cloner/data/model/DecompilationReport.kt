package com.atp.cloner.data.model

data class DecompilationReport(
    val manifestDecodeSuccess: Boolean,
    val resourceDecodeSuccess: Boolean,
    val dexInspectionSuccess: Boolean,
    val detectedComponentsCount: Int,
    val declaredPermissions: List<String>,
    val packageTransformationRisks: List<String>,
    val iconTransformationReadiness: String,
    val toolchainLimitations: List<String>,
    val logs: List<String>
)
