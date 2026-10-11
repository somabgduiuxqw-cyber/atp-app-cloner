package com.atp.cloner.engine.build

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipFile

data class DexValidationInfo(
    val entryName: String,
    val magic: String,
    val adler32Valid: Boolean,
    val sha1Valid: Boolean,
    val fileSize: Int = 0,
    val headerSize: Int = 0,
    val isValid: Boolean = adler32Valid && sha1Valid
)

class ApkDexValidator {

    fun validateDexBytes(entryName: String, bytes: ByteArray): DexValidationInfo {
        if (bytes.size < 0x70) {
            return DexValidationInfo(
                entryName = entryName,
                magic = "invalid",
                adler32Valid = false,
                sha1Valid = false,
                fileSize = bytes.size,
                headerSize = 0
            )
        }

        // Magic 8 bytes: dex\nXXX\0
        val magicBytes = bytes.copyOfRange(0, 8)
        val magicStr = String(magicBytes, Charsets.US_ASCII)
        val isMagicValid = magicBytes[0] == 0x64.toByte() &&
                magicBytes[1] == 0x65.toByte() &&
                magicBytes[2] == 0x78.toByte() &&
                magicBytes[3] == 0x0A.toByte()

        if (!isMagicValid) {
            return DexValidationInfo(
                entryName = entryName,
                magic = magicStr,
                adler32Valid = false,
                sha1Valid = false,
                fileSize = bytes.size,
                headerSize = 0
            )
        }

        // Check Endian tag at offset 40 (0x12345678)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val storedAdler = buf.getInt(8)
        val storedSha1 = bytes.copyOfRange(12, 32)
        val fileSize = buf.getInt(32)
        val headerSize = buf.getInt(36)

        // Validate SHA-1 over offset 32..EOF
        val sha1Digest = MessageDigest.getInstance("SHA-1")
        sha1Digest.update(bytes, 32, bytes.size - 32)
        val computedSha1 = sha1Digest.digest()
        val sha1Matches = storedSha1.contentEquals(computedSha1)

        // Validate Adler32 over offset 12..EOF
        val adler = Adler32()
        adler.update(bytes, 12, bytes.size - 12)
        val computedAdler = adler.value.toInt()
        val adlerMatches = storedAdler == computedAdler

        return DexValidationInfo(
            entryName = entryName,
            magic = magicStr,
            adler32Valid = adlerMatches,
            sha1Valid = sha1Matches,
            fileSize = fileSize,
            headerSize = headerSize,
            isValid = isMagicValid && adlerMatches && sha1Matches
        )
    }

    fun validateAllDexInApk(apkFile: File): List<DexValidationInfo> {
        val results = mutableListOf<DexValidationInfo>()
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".dex")) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    results.add(validateDexBytes(entry.name, bytes))
                }
            }
        }
        return results
    }
}
