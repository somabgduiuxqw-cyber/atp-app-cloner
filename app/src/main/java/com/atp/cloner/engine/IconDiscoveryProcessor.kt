package com.atp.cloner.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.atp.cloner.data.model.DensityIconVariant
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class IconDiscoveryProcessor {

    data class IconScanResult(
        val resolvedType: String, // "Adaptive XML", "Raster PNG/WebP", "Vector Drawable", "Unresolved"
        val foregroundPath: String?,
        val backgroundPath: String?,
        val monochromePath: String?,
        val densityVariants: List<DensityIconVariant>,
        val previewBytes: ByteArray?
    )

    fun discoverIcons(zipFile: ZipFile, iconResName: String, roundIconResName: String?): IconScanResult {
        val cleanName = iconResName.substringAfterLast("/").substringAfterLast(":")
        val targetBase = if (cleanName.isNotEmpty()) cleanName else "ic_launcher"

        var resolvedType = "Raster PNG/WebP"
        var foregroundPath: String? = null
        var backgroundPath: String? = null
        var monochromePath: String? = null
        var previewBytes: ByteArray? = null

        // Check for adaptive icon XML in anydpi-v26
        val adaptiveEntry = zipFile.getEntry("res/mipmap-anydpi-v26/$targetBase.xml")
            ?: zipFile.getEntry("res/drawable-anydpi-v26/$targetBase.xml")
            ?: zipFile.getEntry("res/mipmap-anydpi/$targetBase.xml")

        if (adaptiveEntry != null) {
            resolvedType = "Adaptive XML"
            try {
                zipFile.getInputStream(adaptiveEntry).use { input ->
                    val content = String(input.readBytes(), Charsets.ISO_8859_1)
                    if (content.contains("foreground")) {
                        foregroundPath = extractDrawableRef(content, "foreground")
                    }
                    if (content.contains("background")) {
                        backgroundPath = extractDrawableRef(content, "background")
                    }
                    if (content.contains("monochrome")) {
                        monochromePath = extractDrawableRef(content, "monochrome")
                    }
                }
            } catch (e: Exception) {
                // Keep type as Adaptive XML
            }
        }

        // Enumerate density variants
        val densities = listOf(
            Triple("mdpi", "48x48", 48),
            Triple("hdpi", "72x72", 72),
            Triple("xhdpi", "96x96", 96),
            Triple("xxhdpi", "144x144", 144),
            Triple("xxxhdpi", "192x192", 192)
        )

        val densityVariants = mutableListOf<DensityIconVariant>()

        for ((density, dims, size) in densities) {
            val pngMipmap = zipFile.getEntry("res/mipmap-$density/$targetBase.png")
            val webpMipmap = zipFile.getEntry("res/mipmap-$density/$targetBase.webp")
            val pngDrawable = zipFile.getEntry("res/drawable-$density/$targetBase.png")

            val foundEntry = pngMipmap ?: webpMipmap ?: pngDrawable

            if (foundEntry != null) {
                val format = if (foundEntry.name.endsWith(".webp")) "WebP" else "PNG"
                densityVariants.add(
                    DensityIconVariant(
                        densityName = density,
                        entryPath = foundEntry.name,
                        dimensions = dims,
                        format = format,
                        sizeBytes = foundEntry.size,
                        existsInApk = true
                    )
                )

                // Pick highest quality for preview
                if (previewBytes == null && (density == "xxhdpi" || density == "xhdpi" || density == "hdpi")) {
                    try {
                        zipFile.getInputStream(foundEntry).use { input ->
                            previewBytes = input.readBytes()
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } else {
                densityVariants.add(
                    DensityIconVariant(
                        densityName = density,
                        entryPath = "res/mipmap-$density/$targetBase.png",
                        dimensions = dims,
                        format = "PNG (Missing/Virtual)",
                        sizeBytes = 0,
                        existsInApk = false
                    )
                )
            }
        }

        // If preview is still null, look for any image matching icon
        if (previewBytes == null) {
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if ((e.name.contains("icon", ignoreCase = true) || e.name.contains("launcher", ignoreCase = true))
                    && (e.name.endsWith(".png") || e.name.endsWith(".webp"))
                ) {
                    try {
                        zipFile.getInputStream(e).use { previewBytes = it.readBytes() }
                        break
                    } catch (ex: Exception) {
                        // ignore
                    }
                }
            }
        }

        return IconScanResult(
            resolvedType = resolvedType,
            foregroundPath = foregroundPath,
            backgroundPath = backgroundPath,
            monochromePath = monochromePath,
            densityVariants = densityVariants,
            previewBytes = previewBytes
        )
    }

    private fun extractDrawableRef(xmlText: String, tag: String): String? {
        val idx = xmlText.indexOf(tag)
        if (idx == -1) return null
        val sub = xmlText.substring(idx, minOf(idx + 120, xmlText.length))
        val drawableIdx = sub.indexOf("drawable")
        if (drawableIdx != -1) {
            val refSub = sub.substring(drawableIdx)
            val match = Regex("""@(?:drawable|mipmap)/([a-zA-Z0-9_]+)""").find(refSub)
            if (match != null) return match.value
        }
        return null
    }

    /**
     * Resizes a custom input icon Bitmap into standard density PNG byte arrays.
     */
    fun generateDensityIcons(
        sourceBitmap: Bitmap,
        bgColor: Int? = null
    ): Map<String, ByteArray> {
        val densities = mapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192
        )

        val result = mutableMapOf<String, ByteArray>()

        for ((density, size) in densities) {
            val targetBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(targetBitmap)

            if (bgColor != null) {
                canvas.drawColor(bgColor)
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val scaledSrc = Bitmap.createScaledBitmap(sourceBitmap, size, size, true)
            canvas.drawBitmap(scaledSrc, 0f, 0f, paint)

            val stream = ByteArrayOutputStream()
            targetBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            result[density] = stream.toByteArray()
        }

        return result
    }
}
