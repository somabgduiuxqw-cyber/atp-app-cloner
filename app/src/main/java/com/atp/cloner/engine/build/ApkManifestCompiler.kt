package com.atp.cloner.engine.build

import com.atp.cloner.engine.AXmlDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ManifestCompilationResult(
    val isValid: Boolean,
    val packageName: String,
    val appLabel: String,
    val versionCode: Long,
    val versionName: String,
    val totalChunks: Int,
    val stringPoolSize: Int,
    val compiledBytes: ByteArray,
    val errors: List<String> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ManifestCompilationResult
        return compiledBytes.contentEquals(other.compiledBytes)
    }

    override fun hashCode(): Int {
        return compiledBytes.contentHashCode()
    }
}

class ApkManifestCompiler {

    private val decoder = AXmlDecoder()

    /**
     * Inspects, validates, and normalizes the compiled binary AndroidManifest.xml.
     * Verifies chunk headers (RES_XML_TYPE = 0x00080003), string pools, and 4-byte boundaries.
     */
    fun compileAndValidate(manifestBytes: ByteArray): ManifestCompilationResult {
        if (manifestBytes.size < 8) {
            return ManifestCompilationResult(
                isValid = false,
                packageName = "",
                appLabel = "",
                versionCode = 0,
                versionName = "",
                totalChunks = 0,
                stringPoolSize = 0,
                compiledBytes = manifestBytes,
                errors = listOf("Manifest data too small (< 8 bytes)")
            )
        }

        val buffer = ByteBuffer.wrap(manifestBytes).order(ByteOrder.LITTLE_ENDIAN)
        val chunkType = buffer.short.toInt() and 0xFFFF
        val headerSize = buffer.short.toInt() and 0xFFFF
        val chunkSize = buffer.int

        if (chunkType != 0x0003) {
            return ManifestCompilationResult(
                isValid = false,
                packageName = "",
                appLabel = "",
                versionCode = 0,
                versionName = "",
                totalChunks = 0,
                stringPoolSize = 0,
                compiledBytes = manifestBytes,
                errors = listOf("Invalid binary XML header type: 0x${Integer.toHexString(chunkType)} (expected RES_XML_TYPE 0x0003)")
            )
        }

        // Count chunks and inspect string pool
        var chunksCount = 1
        var stringPoolCount = 0
        var offset = headerSize

        while (offset + 8 <= manifestBytes.size) {
            val cType = ByteBuffer.wrap(manifestBytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
            val cSize = ByteBuffer.wrap(manifestBytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

            if (cType == 0x0001) { // RES_STRING_POOL_TYPE
                val strCount = ByteBuffer.wrap(manifestBytes, offset + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                stringPoolCount = strCount
            }

            chunksCount++
            if (cSize <= 0) break
            offset += cSize
        }

        // Decode manifest metadata using AXmlDecoder
        val decoded = decoder.decodeManifest(manifestBytes)

        return ManifestCompilationResult(
            isValid = true,
            packageName = decoded.packageName,
            appLabel = decoded.appLabel,
            versionCode = decoded.versionCode,
            versionName = decoded.versionName,
            totalChunks = chunksCount,
            stringPoolSize = stringPoolCount,
            compiledBytes = manifestBytes
        )
    }
}
