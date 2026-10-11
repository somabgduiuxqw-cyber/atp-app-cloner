package com.atp.cloner.data.model

data class CloneConfiguration(
    val changeAppName: Boolean = false,
    val newAppName: String = "",
    val changePackageId: Boolean = false,
    val newPackageId: String = "",
    val changeLauncherIcon: Boolean = false,
    val customIconReference: String? = null,
    val customIconBytes: ByteArray? = null,
    val iconBackgroundColor: Int? = null
) {
    fun hasAnyModifications(): Boolean = changeAppName || changePackageId || changeLauncherIcon

    fun activeModificationsSummary(): String {
        val active = mutableListOf<String>()
        if (changeAppName) active.add("Display Name ('$newAppName')")
        if (changePackageId) active.add("Package ID ('$newPackageId')")
        if (changeLauncherIcon) active.add("Custom Launcher Icon")
        return if (active.isEmpty()) "No modifications selected" else active.joinToString(" + ")
    }
}
