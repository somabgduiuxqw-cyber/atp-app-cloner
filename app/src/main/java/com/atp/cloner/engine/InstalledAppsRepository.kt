package com.atp.cloner.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import com.atp.cloner.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class InstalledAppsRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val selfPackageName: String = context.packageName

    suspend fun getInstalledApps(
        includeSystemApps: Boolean = true,
        searchQuery: String = ""
    ): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val appList = mutableListOf<InstalledAppInfo>()

        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_META_DATA
            }

            val installedPackages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(0)
            }

            for (pkgInfo in installedPackages) {
                val pkgName = pkgInfo.packageName ?: continue

                // Exclude self by default
                if (pkgName == selfPackageName) continue

                val appInfo = pkgInfo.applicationInfo ?: continue
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                if (!includeSystemApps && isSystem) continue

                val appName = try {
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkgName
                }

                if (searchQuery.isNotBlank()) {
                    val query = searchQuery.trim().lowercase()
                    if (!appName.lowercase().contains(query) && !pkgName.lowercase().contains(query)) {
                        continue
                    }
                }

                val apkPath = appInfo.sourceDir ?: ""
                val apkFile = if (apkPath.isNotEmpty()) File(apkPath) else null
                val apkSize = if (apkFile != null && apkFile.exists()) apkFile.length() else 0L

                val splitPaths = appInfo.splitSourceDirs
                val isSplit = !splitPaths.isNullOrEmpty()
                val splitCount = splitPaths?.size ?: 0
                val splitNames = splitPaths?.map { File(it).name } ?: emptyList()

                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo.minSdkVersion
                } else {
                    21
                }
                val targetSdk = appInfo.targetSdkVersion

                val iconBase64 = try {
                    val drawable = packageManager.getApplicationIcon(appInfo)
                    drawableToBase64(drawable)
                } catch (e: Exception) {
                    null
                }

                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
                val versionName = pkgInfo.versionName ?: "1.0"

                appList.add(
                    InstalledAppInfo(
                        appName = appName,
                        packageName = pkgName,
                        versionName = versionName,
                        versionCode = versionCode,
                        isSystemApp = isSystem,
                        apkPath = apkPath,
                        apkSize = apkSize,
                        isSplitApk = isSplit,
                        splitCount = splitCount,
                        splitNames = splitNames,
                        iconBase64 = iconBase64,
                        minSdk = minSdk,
                        targetSdk = targetSdk
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        appList.sortedBy { it.appName.lowercase() }
    }

    private fun drawableToBase64(drawable: Drawable): String? {
        return try {
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(192)
                val height = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(192)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
