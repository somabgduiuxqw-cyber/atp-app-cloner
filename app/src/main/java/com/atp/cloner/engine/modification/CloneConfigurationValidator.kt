package com.atp.cloner.engine.modification

import com.atp.cloner.data.model.CloneConfiguration

object CloneConfigurationValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val isNoOp: Boolean,
        val nameError: String? = null,
        val packageError: String? = null,
        val iconError: String? = null,
        val warnings: List<String> = emptyList()
    )

    private val PACKAGE_REGEX = Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$")

    fun validate(
        config: CloneConfiguration,
        originalAppName: String,
        originalPackageId: String
    ): ValidationResult {
        if (!config.hasAnyModifications()) {
            return ValidationResult(
                isValid = false,
                isNoOp = true,
                warnings = listOf("No modifications requested. Enable at least one customization option.")
            )
        }

        var nameError: String? = null
        var packageError: String? = null
        var iconError: String? = null
        val warnings = mutableListOf<String>()

        // 1. Validate App Name
        if (config.changeAppName) {
            val trimmedName = config.newAppName.trim()
            if (trimmedName.isEmpty()) {
                nameError = "Application display name cannot be blank."
            } else if (trimmedName == originalAppName) {
                warnings.add("New application name is identical to the original name.")
            }
        }

        // 2. Validate Package Identifier
        if (config.changePackageId) {
            val trimmedPackage = config.newPackageId.trim()
            if (trimmedPackage.isEmpty()) {
                packageError = "Package identifier cannot be blank."
            } else if (!PACKAGE_REGEX.matches(trimmedPackage)) {
                packageError = "Invalid package identifier. Must contain at least two segments (e.g. 'com.example.app') using letters, numbers, and underscores."
            } else if (trimmedPackage == "com.atp.cloner") {
                packageError = "Cannot change target package ID to 'com.atp.cloner' (reserved by ATP App Cloner)."
            } else if (trimmedPackage == originalPackageId) {
                warnings.add("New package identifier is identical to the original package.")
            } else {
                warnings.add("Changing package identifier may require re-registration for third-party services (OAuth, Google Play Services, Universal Links).")
            }
        }

        // 3. Validate Launcher Icon
        if (config.changeLauncherIcon) {
            if (config.customIconBytes == null && config.customIconReference == null) {
                iconError = "Please select a custom icon image."
            }
        }

        val isValid = (nameError == null) && (packageError == null) && (iconError == null)

        return ValidationResult(
            isValid = isValid,
            isNoOp = false,
            nameError = nameError,
            packageError = packageError,
            iconError = iconError,
            warnings = warnings
        )
    }
}
