package com.atp.cloner.ui.theme

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class DarkThemeVariant(val displayName: String, val hexCode: String) {
    DARK_BLUE("Dark Blue", "#071426"),
    DARK_CHARCOAL("Dark Charcoal", "#121416"),
    DARK_GREEN("Dark Green", "#071A12"),
    DARK_TEAL("Dark Teal", "#071C20")
}
