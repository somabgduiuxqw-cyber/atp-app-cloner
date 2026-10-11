package com.atp.cloner.engine.build

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipFile

data class DexFileInfo(
    val entryName: String,
    val fileSize: Int,
    val magic: String,
    val adler32Valid: Boolean,
    val sha1Valid: Boolean,
    val classDefsCount: Int,
    val methodIdsCount: Int,
    val stringIdsCount: Int
)

data class DexValidationResult(
    val isValid: Boolean,
    val dexFiles: List<DexFileInfo>,
    val totalClasses: Int,
    val allChecksumsValid: Boolean,
    val errors: List<String> = emptyList()
)

class ApkDexValidator {

    /**
     * Validates all DEX files contained within the APK file.
     */
    fun validateApkDexFiles(apkFile: File): DexValidationResult {
        val dexList = mutableListOf<DexFileInfo>()
        val errors = mutableListOf<String>()
        var totalClasses = 0
        var allChecksumsValid = true

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("classes") && entry.name.endsWith(".dex")) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val info = validateDexBytes(entry.name, bytes)
                        dexList.add(info)
                        totalClasses += info.classDefsCount
                        if (!info.adler32Valid || !info.sha1Valid) {
                            allChecksumsValid = false
                            errors.add("DEX checksum or signature mismatch in ${entry.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return DexValidationResult(
                isValid = false,
                dexFiles = emptyList(),
                totalClasses = 0,
                allChecksumsValid = false,
                errors = listOf("Failed reading DEX entries from APK: ${e.message}")
            )
        }

        if (dexList.isEmpty()) {
            return DexValidationResult(
                isValid = false,
                dexFiles = emptyList(),
                totalClasses = 0,
                allChecksumsValid = false,
                errors = listOf("No DEX files found in APK archive")
            )
        }

        return DexValidationResult(
            isValid = errors.isEmpty(),
            dexFiles = dexList,
            totalClasses = totalClasses,
            allChecksumsValid = allChecksumsValid,
            errors = errors
        )
    }

    /**
     * Validates an individual DEX file byte array according to Dalvik/ART specs.
     */
    fun validateDexBytes(name: String, bytes: ByteArray): DexFileInfo {
        if (bytes.size < 0x70) {
            return DexFileInfo(
                entryName = name,
                fileSize = bytes.size,
                magic = "invalid",
                adler32Valid = false,
                sha1Valid = false,
                classDefsCount = 0,
                methodIdsCount = 0,
                stringIdsCount = 0
            )
        }

        val magicBytes = bytes.sliceArray(0 until 8)
        val magicString = String(magicBytes).replace("\u0000", "\\0").replace("\n", "\\n")
        val isMagicValid = magicBytes.take(4).toByteArray().contentEquals(byteArrayOf(0x64, 0x65, 0x78, 0x0A)) // "dex\n"

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(8)
        val recordedChecksum = buffer.int.toLong() and 0xFFFFFFFFL

        val recordedSha1 = ByteArray(20)
        buffer.get(recordedSha1)

        val headerFileSize = buffer.int
        val headerSize = buffer.int
        val endianTag = buffer.int

        buffer.position(56) // string_ids_size
        val stringIdsSize = buffer.int
        buffer.position(64) // type_ids_size
        val typeIdsSize = buffer.int
        buffer.position(72) // proto_ids_size
        val protoIdsSize = buffer.int
        buffer.position(80) // field_ids_size
        val fieldIdsSize = buffer.int
        buffer.position(88) // method_ids_size
        val methodIdsSize = buffer.int
        buffer.position(96) // class_defs_size
        val classDefsSize = buffer.int

        // Calculate Adler32 checksum over all bytes from offset 12 to EOF
        val adler = Adler32()
        adler.update(bytes, 12, bytes.size - 12)
        val computedChecksum = adler.value
        val adlerValid = computedChecksum == recordedChecksum

        // Calculate SHA-1 over all bytes from offset 32 to EOF
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(bytes, 32, bytes.size - 32)
        val computedSha1 = digest.digest()
        val sha1Valid = computedSha1.contentEquals(recordedSha1)

        return DexFileInfo(
            entryName = name,
            fileSize = bytes.size,
            magic = magicString,
            adler32Valid = adlerValid,
            sha1Valid = sha1Valid,
            classDefsCount = classDefsSize,
            methodIdsCount = methodIdsSize,
            stringIdsCount = stringIdsSize
        )
    }
}
