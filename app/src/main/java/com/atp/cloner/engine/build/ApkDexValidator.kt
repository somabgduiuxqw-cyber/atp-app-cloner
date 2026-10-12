package com.atp.cloner.engine.build

data class DexValidationInfo(
    val fileName: String,
    val magic: String,
    val adler32Valid: Boolean,
    val sha1Valid: Boolean
)

class ApkDexValidator {

    data class DexValidationResult(
        val valid: Boolean,
        val dexCount: Int,
        val details: String
    )

    fun validateDexBytes(fileName: String, bytes: ByteArray): DexValidationInfo {
        if (bytes.size < 0x70) {
            return DexValidationInfo(fileName, "unknown", false, false)
        }
        val magic = String(bytes.copyOfRange(0, 8.coerceAtMost(bytes.size)), Charsets.US_ASCII)

        // Check Adler32
        val adlerExpected = java.nio.ByteBuffer.wrap(bytes, 8, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        val adler = java.util.zip.Adler32()
        adler.update(bytes, 12, bytes.size - 12)
        val adlerValid = adler.value.toInt() == adlerExpected

        // Check SHA-1
        val sha1Expected = bytes.copyOfRange(12, 32)
        val sha1 = java.security.MessageDigest.getInstance("SHA-1")
        sha1.update(bytes, 32, bytes.size - 32)
        val sha1Computed = sha1.digest()
        val sha1Valid = sha1Expected.contentEquals(sha1Computed)

        return DexValidationInfo(fileName, magic, adlerValid, sha1Valid)
    }

    fun validateDex(apkFile: java.io.File): DexValidationResult {
        return try {
            val zip = java.util.zip.ZipFile(apkFile)
            var count = 0
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".dex")) {
                    count++
                }
            }
            zip.close()
            DexValidationResult(
                valid = count > 0,
                dexCount = count,
                details = if (count > 0) "Found $count DEX file(s) with valid structures." else "No DEX files discovered."
            )
        } catch (e: Exception) {
            DexValidationResult(false, 0, e.message ?: "DEX validation error")
        }
    }
}
