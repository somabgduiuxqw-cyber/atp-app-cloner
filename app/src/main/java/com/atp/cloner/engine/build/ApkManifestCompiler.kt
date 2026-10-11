package com.atp.cloner.engine.build

import com.atp.cloner.engine.AXmlDecoder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ManifestCompilationResult(
    val isValid: Boolean,
    val packageName: String = "",
    val appLabel: String = "",
    val message: String = "",
    val compiledBytes: ByteArray? = null
)

class ApkManifestCompiler {

    private val aXmlDecoder = AXmlDecoder()

    fun compileAndValidate(bytes: ByteArray): ManifestCompilationResult {
        if (bytes.size < 8) {
            return ManifestCompilationResult(
                isValid = false,
                message = "Manifest byte stream too small for chunk header"
            )
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val chunkType = buf.getShort(0).toInt() and 0xFFFF
        val headerSize = buf.getShort(2).toInt() and 0xFFFF
        val chunkSize = buf.getInt(4)

        // RES_XML_TYPE is 0x0003 with headerSize 8
        if (chunkType != 0x0003 || headerSize != 8) {
            return ManifestCompilationResult(
                isValid = false,
                message = "Invalid RES_XML_TYPE chunk: type=0x%04X, headerSize=%d".format(chunkType, headerSize)
            )
        }

        if (chunkSize > bytes.size || chunkSize < 8) {
            return ManifestCompilationResult(
                isValid = false,
                message = "Invalid chunkSize in XML header: chunkSize=$chunkSize, bufferSize=${bytes.size}"
            )
        }

        return try {
            val decoded = aXmlDecoder.decodeManifest(bytes)
            ManifestCompilationResult(
                isValid = true,
                packageName = decoded.packageName,
                appLabel = decoded.appLabel,
                message = "Valid binary AndroidManifest chunk structure",
                compiledBytes = bytes
            )
        } catch (e: Exception) {
            ManifestCompilationResult(
                isValid = true,
                message = "Header valid, parsed with warnings: ${e.message}",
                compiledBytes = bytes
            )
        }
    }

    fun modifyAndCompile(
        manifestBytes: ByteArray,
        replacements: Map<String, String>
    ): ManifestCompilationResult {
        return try {
            val modified = aXmlDecoder.rewriteManifestStrings(manifestBytes, replacements)
            val validation = compileAndValidate(modified)
            if (validation.isValid) {
                ManifestCompilationResult(
                    isValid = true,
                    packageName = validation.packageName,
                    appLabel = validation.appLabel,
                    message = "Successfully recompiled manifest strings",
                    compiledBytes = modified
                )
            } else {
                validation
            }
        } catch (e: Exception) {
            ManifestCompilationResult(
                isValid = false,
                message = "Failed to rewrite manifest: ${e.message}"
            )
        }
    }
}
