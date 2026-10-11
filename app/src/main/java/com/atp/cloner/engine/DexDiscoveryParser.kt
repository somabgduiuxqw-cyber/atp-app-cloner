package com.atp.cloner.engine

import com.atp.cloner.data.model.ConfidenceLevel
import com.atp.cloner.data.model.DexReferenceDiscovery
import com.atp.cloner.data.model.DexReferenceType
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexDiscoveryParser {

    data class DexScanResult(
        val dexFileName: String,
        val totalStringsCount: Int,
        val totalClassesCount: Int,
        val references: List<DexReferenceDiscovery>
    )

    fun scanDex(bytes: ByteArray, dexName: String, targetPackage: String): DexScanResult {
        if (bytes.size < 112) {
            return DexScanResult(dexName, 0, 0, emptyList())
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Check magic "dex\n035\0"
        val magic = ByteArray(8)
        buffer.get(magic)
        val magicStr = String(magic, Charsets.US_ASCII)
        if (!magicStr.startsWith("dex\n")) {
            return DexScanResult(dexName, 0, 0, emptyList())
        }

        buffer.position(32)
        val fileSize = buffer.int
        val headerSize = buffer.int
        val endianTag = buffer.int
        buffer.position(56)
        val stringIdsSize = buffer.int
        val stringIdsOff = buffer.int
        val typeIdsSize = buffer.int
        val typeIdsOff = buffer.int
        buffer.position(96)
        val classDefsSize = buffer.int
        val classDefsOff = buffer.int

        if (stringIdsOff <= 0 || stringIdsOff >= bytes.size || stringIdsSize <= 0 || stringIdsSize > 500000) {
            return DexScanResult(dexName, 0, classDefsSize, emptyList())
        }

        val stringOffsets = IntArray(minOf(stringIdsSize, 100000))
        buffer.position(stringIdsOff)
        for (i in stringOffsets.indices) {
            if (buffer.remaining() < 4) break
            stringOffsets[i] = buffer.int
        }

        val slashPackage = targetPackage.replace('.', '/')
        val classDescriptorPrefix = "L$slashPackage"

        val foundReferences = mutableListOf<DexReferenceDiscovery>()

        for (i in stringOffsets.indices) {
            val off = stringOffsets[i]
            if (off in 0 until bytes.size) {
                val str = readUleb128String(bytes, off)

                if (str.startsWith(classDescriptorPrefix)) {
                    foundReferences.add(
                        DexReferenceDiscovery(
                            dexFileName = dexName,
                            referenceType = DexReferenceType.CLASS_DESCRIPTOR,
                            matchedValue = str,
                            contextDescription = "Compiled Class Descriptor ($str)",
                            confidence = ConfidenceLevel.CONFIRMED_DEX_STRUCTURAL
                        )
                    )
                } else if (str == targetPackage) {
                    foundReferences.add(
                        DexReferenceDiscovery(
                            dexFileName = dexName,
                            referenceType = DexReferenceType.PACKAGE_PREFIX_MATCH,
                            matchedValue = str,
                            contextDescription = "Exact Package Identifier string constant",
                            confidence = ConfidenceLevel.CONFIRMED_DEX_STRUCTURAL
                        )
                    )
                } else if (str == slashPackage) {
                    foundReferences.add(
                        DexReferenceDiscovery(
                            dexFileName = dexName,
                            referenceType = DexReferenceType.TYPE_DESCRIPTOR,
                            matchedValue = str,
                            contextDescription = "Slash-delimited package path constant",
                            confidence = ConfidenceLevel.CONFIRMED_DEX_STRUCTURAL
                        )
                    )
                } else if (str.contains(targetPackage)) {
                    foundReferences.add(
                        DexReferenceDiscovery(
                            dexFileName = dexName,
                            referenceType = DexReferenceType.STRING_CONSTANT,
                            matchedValue = str,
                            contextDescription = "String constant containing package substring",
                            confidence = ConfidenceLevel.POTENTIAL_STRING_MATCH
                        )
                    )
                }
            }
        }

        return DexScanResult(
            dexFileName = dexName,
            totalStringsCount = stringIdsSize,
            totalClassesCount = classDefsSize,
            references = foundReferences
        )
    }

    private fun readUleb128String(bytes: ByteArray, offset: Int): String {
        var p = offset
        if (p >= bytes.size) return ""
        // Read ULEB128 length
        var result = 0
        var shift = 0
        while (p < bytes.size) {
            val b = bytes[p++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        val maxChars = minOf(result, 256)
        val end = minOf(p + (maxChars * 3), bytes.size)
        // Find null terminator
        var nullPos = p
        while (nullPos < end && bytes[nullPos] != 0.toByte()) {
            nullPos++
        }
        val len = nullPos - p
        return if (len > 0) String(bytes, p, len, Charsets.UTF_8) else ""
    }
}
