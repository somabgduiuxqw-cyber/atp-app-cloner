package com.atp.cloner.engine.build

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ArscRebuildResult(
    val isValid: Boolean,
    val packageCount: Int,
    val is4ByteAligned: Boolean
)

class ApkResourceRebuilder {

    fun rebuildAndValidate(bytes: ByteArray): ArscRebuildResult {
        if (bytes.size < 16) return ArscRebuildResult(false, 0, false)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val type = buf.short.toInt() and 0xFFFF
        val headerSize = buf.short.toInt() and 0xFFFF
        val chunkSize = buf.int
        val packageCount = buf.int

        val isValid = type == 0x0002 && headerSize >= 12
        val is4ByteAligned = (bytes.size % 4) == 0
        return ArscRebuildResult(isValid, packageCount, is4ByteAligned)
    }
}
