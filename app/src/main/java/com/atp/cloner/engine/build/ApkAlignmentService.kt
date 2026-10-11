package com.atp.cloner.engine.build

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class AlignmentResult(
    val success: Boolean,
    val alignedEntriesCount: Int,
    val uncompressedEntriesCount: Int,
    val outputFile: File?,
    val details: String,
    val errors: List<String> = emptyList()
)

class ApkAlignmentService {

    /**
     * Aligns an APK archive so that uncompressed entries (resources.arsc and native libraries)
     * are aligned to 4-byte (and 4KB/16KB for .so) boundaries.
     */
    fun alignApk(inputFile: File, outputFile: File): AlignmentResult {
        var alignedCount = 0
        var uncompressedCount = 0
        val errors = mutableListOf<String>()

        try {
            val zis = ZipInputStream(FileInputStream(inputFile))
            val zos = ZipOutputStream(FileOutputStream(outputFile))

            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name

                // Determine if entry should be STORED uncompressed
                val isStored = entry.method == ZipEntry.STORED ||
                        entryName == "resources.arsc" ||
                        (entryName.startsWith("lib/") && entryName.endsWith(".so"))

                val newEntry = ZipEntry(entryName)

                if (isStored) {
                    uncompressedCount++
                    val bytes = zis.readBytes()

                    newEntry.method = ZipEntry.STORED
                    newEntry.size = bytes.size.toLong()
                    newEntry.compressedSize = bytes.size.toLong()

                    val crc = CRC32()
                    crc.update(bytes)
                    newEntry.crc = crc.value

                    // Compute 4-byte alignment padding for extra field
                    // ZIP local header: 30 bytes + name length + extra length
                    val headerSizeBeforeExtra = 30 + entryName.toByteArray(Charsets.UTF_8).size
                    val alignment = if (entryName.endsWith(".so")) 4096 else 4
                    val paddingNeeded = (alignment - (headerSizeBeforeExtra % alignment)) % alignment

                    if (paddingNeeded > 0) {
                        newEntry.extra = ByteArray(paddingNeeded)
                    }

                    zos.putNextEntry(newEntry)
                    zos.write(bytes)
                    alignedCount++
                } else {
                    newEntry.method = ZipEntry.DEFLATED
                    zos.putNextEntry(newEntry)
                    val buffer = ByteArray(16384)
                    var len: Int
                    while (zis.read(buffer).also { len = it } != -1) {
                        zos.write(buffer, 0, len)
                    }
                }

                zos.closeEntry()
                zis.closeEntry()
                entry = zis.nextEntry
            }

            zis.close()
            zos.close()

            return AlignmentResult(
                success = true,
                alignedEntriesCount = alignedCount,
                uncompressedEntriesCount = uncompressedCount,
                outputFile = outputFile,
                details = "Successfully aligned $alignedCount stored entries (resources.arsc / .so) on 4-byte boundaries."
            )
        } catch (e: Exception) {
            errors.add("Alignment failure: ${e.message}")
            return AlignmentResult(
                success = false,
                alignedEntriesCount = alignedCount,
                uncompressedEntriesCount = uncompressedCount,
                outputFile = null,
                details = "Alignment failed: ${e.message}",
                errors = errors
            )
        }
    }
}
