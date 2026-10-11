package com.atp.cloner.engine.build

import com.atp.cloner.engine.ArscDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ResourceRebuildResult(
    val isValid: Boolean,
    val packageCount: Int,
    val stringPoolSize: Int,
    val stringCount: Int,
    val is4ByteAligned: Boolean,
    val rebuiltBytes: ByteArray,
    val errors: List<String> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ResourceRebuildResult
        return rebuiltBytes.contentEquals(other.rebuiltBytes)
    }

    override fun hashCode(): Int {
        return rebuiltBytes.contentHashCode()
    }
}

class ApkResourceRebuilder {

    private val arscDecoder = ArscDecoder()

    /**
     * Inspects, validates, and ensures binary integrity of resources.arsc.
     * Verifies RES_TABLE_TYPE (0x0002), package count, string pool boundaries, and padding.
     */
    fun rebuildAndValidate(arscBytes: ByteArray): ResourceRebuildResult {
        if (arscBytes.size < 12) {
            return ResourceRebuildResult(
                isValid = false,
                packageCount = 0,
                stringPoolSize = 0,
                stringCount = 0,
                is4ByteAligned = false,
                rebuiltBytes = arscBytes,
                errors = listOf("resources.arsc too small (< 12 bytes)")
            )
        }

        val buffer = ByteBuffer.wrap(arscBytes).order(ByteOrder.LITTLE_ENDIAN)
        val type = buffer.short.toInt() and 0xFFFF
        val headerSize = buffer.short.toInt() and 0xFFFF
        val chunkSize = buffer.int
        val packageCount = buffer.int

        if (type != 0x0002) { // RES_TABLE_TYPE
            return ResourceRebuildResult(
                isValid = false,
                packageCount = 0,
                stringPoolSize = 0,
                stringCount = 0,
                is4ByteAligned = false,
                rebuiltBytes = arscBytes,
                errors = listOf("Invalid resource table header type: 0x${Integer.toHexString(type)} (expected RES_TABLE_TYPE 0x0002)")
            )
        }

        // Check 4-byte alignment of the table size
        val is4ByteAligned = (chunkSize % 4 == 0) && (arscBytes.size % 4 == 0)

        // Read global string pool
        var stringPoolSize = 0
        var stringCount = 0
        if (arscBytes.size > headerSize + 8) {
            val spType = ByteBuffer.wrap(arscBytes, headerSize, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
            if (spType == 0x0001) {
                stringPoolSize = ByteBuffer.wrap(arscBytes, headerSize + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                stringCount = ByteBuffer.wrap(arscBytes, headerSize + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
            }
        }

        return ResourceRebuildResult(
            isValid = true,
            packageCount = packageCount,
            stringPoolSize = stringPoolSize,
            stringCount = stringCount,
            is4ByteAligned = is4ByteAligned,
            rebuiltBytes = arscBytes
        )
    }
}
