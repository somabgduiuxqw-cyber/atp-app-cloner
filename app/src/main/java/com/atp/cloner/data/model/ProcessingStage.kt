package com.atp.cloner.data.model

enum class ProcessingStage(val displayName: String, val stepNumber: Int, val totalSteps: Int = 12) {
    IMPORT_VALIDATION("Import Validation", 1),
    ARCHIVE_INSPECTION("APK Archive Inspection", 2),
    COMPATIBILITY_ANALYSIS("Compatibility Analysis", 3),
    MANIFEST_DECODING("Binary Manifest Decoding", 4),
    RESOURCE_DECODING("Resource Table & Strings Decoding", 5),
    DEX_INSPECTION("DEX Bytecode Inspection", 6),
    APPLYING_MANIFEST_MODS("Applying Manifest Modifications", 7),
    APPLYING_ICON_MODS("Transforming Launcher Icon Resources", 8),
    REPACKAGING_ARCHIVE("Repackaging APK Archive", 9),
    ZIP_ALIGNMENT("4-Byte ZipAlign Alignment", 10),
    CRYPTOGRAPHIC_SIGNING("Local Cryptographic Signing", 11),
    FINAL_VALIDATION("Output Verification & Checksum", 12),
    COMPLETED("Cloning Completed Successfully", 12)
}

sealed class ProcessingState {
    data object Idle : ProcessingState()
    data class Running(
        val stage: ProcessingStage,
        val progressPercent: Int,
        val stageMessage: String,
        val logMessages: List<String>
    ) : ProcessingState()
    data class Success(val report: ValidationReport) : ProcessingState()
    data class Error(
        val message: String,
        val stage: ProcessingStage,
        val technicalDetail: String? = null,
        val nextStep: String? = null,
        val logMessages: List<String> = emptyList()
    ) : ProcessingState()
}
