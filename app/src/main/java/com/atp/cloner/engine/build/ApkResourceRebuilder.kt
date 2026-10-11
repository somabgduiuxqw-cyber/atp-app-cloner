package com.atp.cloner.engine.build

import com.atp.cloner.engine.ArscDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ResourceValidationResult(
    val isValid: Boolean,
    val packageCount: Int = 0,
    val is4ByteAligned: Boolean = false,
    val message: String = "",
    val recompiledBytes: ByteArray? = null
)

class ApkResourceRebuilder {

    private val arscDecoder = ArscDecoder()

    fun rebuildAndValidate(bytes: ByteArray): ResourceValidationResult {
        if (bytes.size < 12) {
            return ResourceValidationResult(
                isValid = false,
                packageCount = 0,
                is4ByteAligned = false,
                message = "Resource table byte stream too small for header"
            )
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val chunkType = buf.getShort(0).toInt() and 0xFFFF
        val headerSize = buf.getShort(2).toInt() and 0xFFFF
        val chunkSize = buf.getInt(4)

        // RES_TABLE_TYPE is 0x0002 with headerSize 12
        if (chunkType != 0x0002 || headerSize != 12) {
            return ResourceValidationResult(
                isValid = false,
                packageCount = 0,
                is4ByteAligned = (bytes.size % 4 == 0),
                message = "Invalid RES_TABLE_TYPE chunk: type=0x%04X, headerSize=%d".format(chunkType, headerSize)
            )
        }

        val packageCount = buf.getInt(8)
        val is4ByteAligned = (bytes.size % 4 == 0)

        return ResourceValidationResult(
            isValid = true,
            packageCount = packageCount,
            is4ByteAligned = is4ByteAligned,
            message = "Valid binary resources.arsc table structure",
            recompiledBytes = bytes
        )
    }

    fun modifyAndRebuild(
        arscBytes: ByteArray,
        replacements: Map<String, String>
    ): ResourceValidationResult {
        return try {
            val modified = arscDecoder.rewriteArscStrings(arscBytes, replacements)
            val validation = rebuildAndValidate(modified)
            if (validation.isValid) {
                ResourceValidationResult(
                    isValid = true,
                    packageCount = validation.packageCount,
                    is4ByteAligned = validation.is4ByteAligned,
                    message = "Successfully updated resources.arsc string pool",
                    recompiledBytes = modified
                )
            } else {
                validation
            }
        } catch (e: Exception) {
            ResourceValidationResult(
                isValid = false,
                message = "Failed to rewrite resources.arsc: ${e.message}"
            )
        }
    }
}
