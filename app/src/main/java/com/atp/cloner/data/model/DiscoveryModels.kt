package com.atp.cloner.data.model

enum class ConfidenceLevel(val displayName: String, val badgeColorHex: Long) {
    CONFIRMED_MANIFEST("Confirmed Manifest Field", 0xFF10B981),
    RESOLVED_RESOURCE("Resolved Resource Reference", 0xFF2563EB),
    CONFIRMED_DEX_STRUCTURAL("Confirmed DEX Structural Reference", 0xFF8B5CF6),
    POTENTIAL_STRING_MATCH("Potential String Match", 0xFFF59E0B),
    UNRESOLVED_REFERENCE("Unresolved Reference", 0xFFEF4444),
    UNSUPPORTED_CONTENT("Unsupported / Blind Spot", 0xFF64748B)
}

enum class ModificationRisk(val displayName: String, val colorHex: Long) {
    LOW("Low Risk", 0xFF10B981),
    MEDIUM("Medium Risk", 0xFFF59E0B),
    HIGH("High Risk", 0xFFEF4444),
    CRITICAL("Critical Risk", 0xFFDC2626)
}

data class ModificationTarget(
    val category: String, // "App Name", "Launcher Icon", "Package ID"
    val location: String, // e.g. "AndroidManifest.xml: application/@android:label"
    val currentValue: String,
    val targetDescription: String,
    val riskLevel: ModificationRisk,
    val confidence: ConfidenceLevel
)

data class ActivityLabelDiscovery(
    val componentName: String,
    val rawLabel: String,
    val resolvedLabel: String,
    val isLauncher: Boolean,
    val isAlias: Boolean,
    val confidence: ConfidenceLevel
)

data class ActivityIconDiscovery(
    val componentName: String,
    val rawIcon: String,
    val resolvedIconPath: String?,
    val isLauncher: Boolean,
    val isAlias: Boolean,
    val confidence: ConfidenceLevel
)

data class DensityIconVariant(
    val densityName: String, // mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi
    val entryPath: String,
    val dimensions: String, // e.g. "48x48", "72x72"
    val format: String, // PNG, WebP, XML
    val sizeBytes: Long,
    val existsInApk: Boolean
)

data class ComponentClassDiscovery(
    val className: String,
    val componentType: String, // Activity, Service, Receiver, Provider
    val isInManifestPackage: Boolean,
    val isExported: Boolean
)

data class ProviderAuthorityDiscovery(
    val authority: String,
    val providerClass: String,
    val isPackageRelated: Boolean,
    val collisionRisk: Boolean
)

enum class DexReferenceType {
    CLASS_DESCRIPTOR, // Lcom/example/sample/MainActivity;
    TYPE_DESCRIPTOR,
    METHOD_SIGNATURE,
    STRING_CONSTANT,
    PACKAGE_PREFIX_MATCH
}

data class DexReferenceDiscovery(
    val dexFileName: String, // classes.dex, classes2.dex
    val referenceType: DexReferenceType,
    val matchedValue: String,
    val contextDescription: String,
    val confidence: ConfidenceLevel
)

data class AssetMatchDiscovery(
    val assetPath: String,
    val fileType: String, // JSON, XML, Properties, Text
    val matchedString: String,
    val contextSnippet: String,
    val confidence: ConfidenceLevel
)

data class NativeLibDiscovery(
    val abi: String, // arm64-v8a, armeabi-v7a, x86, x86_64
    val libName: String,
    val fileSizeBytes: Long
)

data class CompatibilityRisk(
    val title: String,
    val category: String, // Package, Icon, Name, Architecture
    val severity: ModificationRisk,
    val description: String,
    val mitigationRecommendation: String
)

data class ResourceGraphNode(
    val id: String,
    val label: String,
    val resourceType: String, // Manifest, String, Mipmap, Drawable, AdaptiveXml, DEX, Asset
    val archivePathOrResId: String,
    val decodedValue: String?,
    val confidence: ConfidenceLevel
)

data class ResourceGraphEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val relationshipType: String // "references", "resolves_to", "contains_layer", "declares_authority"
)

data class ResourceReferenceGraph(
    val nodes: List<ResourceGraphNode> = emptyList(),
    val edges: List<ResourceGraphEdge> = emptyList()
)

data class AppNameDiscoveryReport(
    val manifestRawLabel: String,
    val resolvedDefaultLabel: String,
    val labelResourceId: Int?,
    val labelResourceName: String?,
    val localizedVariants: Map<String, String>, // locale code -> translated label
    val activityLabels: List<ActivityLabelDiscovery>,
    val shortcutLabels: List<String>,
    val unresolvedReferences: List<String>,
    val recommendedTargets: List<ModificationTarget>
)

data class LauncherIconDiscoveryReport(
    val manifestRawIcon: String,
    val manifestRoundIcon: String?,
    val resolvedIconType: String, // "Adaptive XML", "Raster PNG/WebP", "Vector Drawable", "Unresolved"
    val foregroundResource: String?,
    val backgroundResource: String?,
    val monochromeResource: String?,
    val densityVariants: List<DensityIconVariant>,
    val activityIcons: List<ActivityIconDiscovery>,
    val previewIconBytes: ByteArray?,
    val unresolvedReferences: List<String>,
    val recommendedTargets: List<ModificationTarget>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LauncherIconDiscoveryReport
        return manifestRawIcon == other.manifestRawIcon && resolvedIconType == other.resolvedIconType
    }

    override fun hashCode(): Int = manifestRawIcon.hashCode()
}

data class PackageReferenceDiscoveryReport(
    val manifestPackage: String,
    val componentClasses: List<ComponentClassDiscovery>,
    val providerAuthorities: List<ProviderAuthorityDiscovery>,
    val dexReferences: List<DexReferenceDiscovery>,
    val assetMatches: List<AssetMatchDiscovery>,
    val nativeLibraries: List<NativeLibDiscovery>,
    val compatibilityRisks: List<CompatibilityRisk>,
    val uninspectedBlindSpots: List<String>,
    val recommendedTargets: List<ModificationTarget>
)

data class ApkMetadataDiscoveryReport(
    val projectId: String,
    val originalApkChecksum: String,
    val originalFileName: String,
    val originalFileSize: Long,
    val scanTimestamp: Long = System.currentTimeMillis(),
    val appNameDiscovery: AppNameDiscoveryReport,
    val launcherIconDiscovery: LauncherIconDiscoveryReport,
    val packageDiscovery: PackageReferenceDiscoveryReport,
    val resourceGraph: ResourceReferenceGraph,
    val scanLogs: List<String>,
    val completenessConfidence: Float = 0.95f
)
