package com.atp.cloner.engine.modification

import com.atp.cloner.data.model.ApkMetadataDiscoveryReport
import com.atp.cloner.data.model.CloneConfiguration

data class ApkModificationPlan(
    val changeAppName: Boolean,
    val targetAppName: String,
    val originalAppName: String,
    val changePackageId: Boolean,
    val targetPackageId: String,
    val originalPackageId: String,
    val changeLauncherIcon: Boolean,
    val plannedManifestChanges: List<String>,
    val plannedResourceChanges: List<String>,
    val plannedIconReplacements: List<String>,
    val safetyWarnings: List<String>,
    val isNoOp: Boolean
)

object ApkModificationPlanner {

    fun createPlan(
        config: CloneConfiguration,
        discoveryReport: ApkMetadataDiscoveryReport
    ): ApkModificationPlan {
        if (!config.hasAnyModifications()) {
            return ApkModificationPlan(
                changeAppName = false,
                targetAppName = "",
                originalAppName = discoveryReport.appNameDiscovery.resolvedDefaultLabel,
                changePackageId = false,
                targetPackageId = "",
                originalPackageId = discoveryReport.packageDiscovery.manifestPackage,
                changeLauncherIcon = false,
                plannedManifestChanges = emptyList(),
                plannedResourceChanges = emptyList(),
                plannedIconReplacements = emptyList(),
                safetyWarnings = listOf("No modifications requested."),
                isNoOp = true
            )
        }

        val manifestChanges = mutableListOf<String>()
        val resourceChanges = mutableListOf<String>()
        val iconReplacements = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (config.changeAppName) {
            val newName = config.newAppName.trim()
            manifestChanges.add("Update <application android:label> to '$newName'")
            resourceChanges.add("Update string resource '${discoveryReport.appNameDiscovery.labelResourceName ?: "app_name"}' to '$newName'")
            if (discoveryReport.appNameDiscovery.localizedVariants.size > 1) {
                resourceChanges.add("Update ${discoveryReport.appNameDiscovery.localizedVariants.size} localized string table configurations")
            }
        }

        if (config.changePackageId) {
            val newPkg = config.newPackageId.trim()
            manifestChanges.add("Update manifest package to '$newPkg'")
            manifestChanges.add("Update ${discoveryReport.packageDiscovery.componentClasses.size} component class references")
            if (discoveryReport.packageDiscovery.providerAuthorities.isNotEmpty()) {
                manifestChanges.add("Update ${discoveryReport.packageDiscovery.providerAuthorities.size} ContentProvider authorities to prevent URI collisions")
            }
            warnings.addAll(discoveryReport.packageDiscovery.compatibilityRisks.map { it.title + ": " + it.description })
        }

        if (config.changeLauncherIcon) {
            val densities = listOf("mdpi (48x48)", "hdpi (72x72)", "xhdpi (96x96)", "xxhdpi (144x144)", "xxxhdpi (192x192)")
            densities.forEach { d ->
                iconReplacements.add("Generate & inject density asset: $d")
            }
            if (discoveryReport.launcherIconDiscovery.resolvedIconType == "Adaptive XML") {
                iconReplacements.add("Update adaptive icon foreground raster layer")
            }
        }

        return ApkModificationPlan(
            changeAppName = config.changeAppName,
            targetAppName = config.newAppName.trim(),
            originalAppName = discoveryReport.appNameDiscovery.resolvedDefaultLabel,
            changePackageId = config.changePackageId,
            targetPackageId = config.newPackageId.trim(),
            originalPackageId = discoveryReport.packageDiscovery.manifestPackage,
            changeLauncherIcon = config.changeLauncherIcon,
            plannedManifestChanges = manifestChanges,
            plannedResourceChanges = resourceChanges,
            plannedIconReplacements = iconReplacements,
            safetyWarnings = warnings,
            isNoOp = false
        )
    }
}
