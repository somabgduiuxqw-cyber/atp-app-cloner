package com.atp.cloner.data.model

data class ApkMetadata(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val sha256Checksum: String,
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val launcherActivity: String?,
    val iconResourceName: String?,
    val iconBytes: ByteArray? = null,
    val hasAdaptiveIcon: Boolean = false,
    val dexFileCount: Int = 1,
    val totalDexSize: Long = 0,
    val totalEntriesCount: Int = 0,
    val nativeAbis: List<String> = emptyList(),
    val providerAuthorities: List<String> = emptyList(),
    val hasSignature: Boolean = false,
    val signatureCertFingerprint: String? = null,
    val signatureType: String? = null,
    val isSupported: Boolean = true,
    val compatibilityWarnings: List<String> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ApkMetadata
        return filePath == other.filePath && sha256Checksum == other.sha256Checksum
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + sha256Checksum.hashCode()
        return result
    }
}
