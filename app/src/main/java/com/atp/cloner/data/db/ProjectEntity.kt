package com.atp.cloner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val projectName: String,
    val originalFileName: String,
    val originalFilePath: String,
    val originalFileSize: Long,
    val originalSha256: String,
    val originalPackageName: String,
    val originalAppLabel: String,
    val originalVersionName: String,
    val originalVersionCode: Long,
    val iconCachedPath: String?,
    val hasAdaptiveIcon: Boolean,
    val dexFileCount: Int,
    val nativeAbis: String,
    val hasSignature: Boolean,
    val certFingerprint: String?,
    val decompilationStatus: String = "Analyzed",
    val enableNameChange: Boolean = false,
    val modifiedAppName: String = "",
    val enablePackageChange: Boolean = false,
    val modifiedPackageName: String = "",
    val enableIconChange: Boolean = false,
    val customIconPath: String? = null,
    val lastStatus: String = "Ready",
    val lastErrorMessage: String? = null,
    val outputApkPath: String? = null,
    val outputSha256: String? = null,
    val outputFileSize: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
