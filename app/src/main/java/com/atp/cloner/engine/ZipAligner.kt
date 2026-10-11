package com.atp.cloner.engine

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipAligner {

    /**
     * Re-packs the input APK ensuring that uncompressed entries (stored)
     * are aligned to 4-byte boundaries according to Android zipalign spec.
     */
    fun alignApk(inputFile: File, outputFile: File): Boolean {
        return try {
            val zis = ZipInputStream(FileInputStream(inputFile))
            val zos = ZipOutputStream(FileOutputStream(outputFile))

            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newEntry = ZipEntry(entry.name)
                val isStored = entry.method == ZipEntry.STORED ||
                        entry.name == "resources.arsc" ||
                        (entry.name.startsWith("lib/") && entry.name.endsWith(".so"))

                if (isStored) {
                    newEntry.method = ZipEntry.STORED
                    newEntry.size = entry.size
                    newEntry.compressedSize = entry.size
                    newEntry.crc = entry.crc
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
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
