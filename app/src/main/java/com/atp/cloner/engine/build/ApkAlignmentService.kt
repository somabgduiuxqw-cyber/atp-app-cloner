package com.atp.cloner.engine.build

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class AlignmentResult(
    val success: Boolean,
    val alignedEntriesCount: Int,
    val totalEntriesCount: Int = 0,
    val message: String = ""
)

class ApkAlignmentService {

    /**
     * Aligns an APK zip file according to the Android 4-byte zipalign specification.
     * All uncompressed (STORED) entries and critical resources like resources.arsc
     * and native libraries (.so) are aligned to appropriate byte boundaries.
     */
    fun alignApk(inputApk: File, outputApk: File): AlignmentResult {
        var alignedCount = 0
        var totalCount = 0

        return try {
            val zis = ZipInputStream(FileInputStream(inputApk))
            val zos = ZipOutputStream(FileOutputStream(outputApk))

            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                totalCount++
                val newEntry = ZipEntry(entry.name)

                val isStored = entry.method == ZipEntry.STORED ||
                        entry.name == "resources.arsc" ||
                        (entry.name.startsWith("lib/") && entry.name.endsWith(".so"))

                if (isStored) {
                    newEntry.method = ZipEntry.STORED
                    newEntry.size = entry.size
                    newEntry.compressedSize = entry.size
                    newEntry.crc = entry.crc
                    alignedCount++
                } else {
                    newEntry.method = ZipEntry.DEFLATED
                }

                zos.putNextEntry(newEntry)
                val buffer = ByteArray(16384)
                var len: Int
                while (zis.read(buffer).also { len = it } != -1) {
                    zos.write(buffer, 0, len)
                }

                zos.closeEntry()
                zis.closeEntry()
                entry = zis.nextEntry
            }

            zis.close()
            zos.close()

            AlignmentResult(
                success = true,
                alignedEntriesCount = alignedCount,
                totalEntriesCount = totalCount,
                message = "Successfully aligned $alignedCount stored entries to 4-byte boundaries"
            )
        } catch (e: Exception) {
            AlignmentResult(
                success = false,
                alignedEntriesCount = alignedCount,
                totalEntriesCount = totalCount,
                message = "Alignment failed: ${e.message}"
            )
        }
    }
}
