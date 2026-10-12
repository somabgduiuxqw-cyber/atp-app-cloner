package com.atp.cloner.data.model

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val apkPath: String,
    val apkSize: Long,
    val isSplitApk: Boolean,
    val splitCount: Int = 0,
    val splitNames: List<String> = emptyList(),
    val iconBase64: String? = null,
    val minSdk: Int = 21,
    val targetSdk: Int = 34
)
