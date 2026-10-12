package com.atp.cloner.engine.build

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ManifestCompilationResult(
    val isValid: Boolean,
    val details: String = ""
)

class ApkManifestCompiler {

    fun compileAndValidate(bytes: ByteArray): ManifestCompilationResult {
        if (bytes.size < 8) return ManifestCompilationResult(false, "Too small for AXML header")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val chunkType = buf.short.toInt() and 0xFFFF
        val headerSize = buf.short.toInt() and 0xFFFF
        val chunkSize = buf.int

        // Standard RES_XML_TYPE is 0x0003
        val isValid = chunkType == 0x0003 && headerSize == 8 && chunkSize <= bytes.size
        return ManifestCompilationResult(
            isValid = isValid,
            details = if (isValid) "Valid binary XML header" else "Invalid chunk type: $chunkType"
        )
    }
}
