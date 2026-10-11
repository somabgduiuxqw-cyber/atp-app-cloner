package com.atp.cloner.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ArscDecoder {

    data class ArscData(
        val globalStrings: List<String> = emptyList(),
        val appNameCandidates: List<String> = emptyList(),
        val localizedAppNames: Map<String, String> = emptyMap(),
        val iconPaths: List<String> = emptyList(),
        val stringResourceNames: List<String> = emptyList()
    )

    fun decodeArsc(bytes: ByteArray): ArscData {
        if (bytes.size < 12) return ArscData()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val tableType = buffer.short.toInt() and 0xFFFF
        val headerSize = buffer.short.toInt() and 0xFFFF
        val tableSize = buffer.int
        val packageCount = buffer.int

        if (tableType != 0x0002) return ArscData()

        val globalStrings = mutableListOf<String>()
        val appNameCandidates = mutableListOf<String>()
        val localizedAppNames = mutableMapOf<String, String>()
        val iconPaths = mutableListOf<String>()
        val stringResourceNames = mutableListOf<String>()

        // Immediately following header is the global String Pool chunk (0x0001)
        if (buffer.remaining() >= 28) {
            val poolChunkStart = buffer.position()
            val chunkType = buffer.short.toInt() and 0xFFFF
            val poolHeaderSize = buffer.short.toInt() and 0xFFFF
            val poolChunkSize = buffer.int

            if (chunkType == 0x0001 && poolChunkSize > 0 && poolChunkStart + poolChunkSize <= bytes.size) {
                val poolStrings = parseStringPool(bytes, poolChunkStart)
                globalStrings.addAll(poolStrings)

                // Search for app name candidates and icon paths
                for (str in poolStrings) {
                    if (str.startsWith("res/mipmap") || str.startsWith("res/drawable")) {
                        if (str.contains("icon", ignoreCase = true) || str.contains("launcher", ignoreCase = true)) {
                            iconPaths.add(str)
                        }
                    }
                    if (str == "app_name" || str == "application_name" || str == "appname" || str == "launcher_name") {
                        stringResourceNames.add(str)
                    }
                }
            }
        }

        // Search for localized strings if present in the raw resource table
        // By scanning for locale qualifiers like "es", "hi", "fr", "de", "zh" near app labels
        extractLocalizedHints(bytes, globalStrings, localizedAppNames)

        return ArscData(
            globalStrings = globalStrings,
            appNameCandidates = appNameCandidates,
            localizedAppNames = localizedAppNames,
            iconPaths = iconPaths,
            stringResourceNames = stringResourceNames
        )
    }

    private fun extractLocalizedHints(
        bytes: ByteArray,
        globalStrings: List<String>,
        localizedMap: MutableMap<String, String>
    ) {
        val candidateLocales = listOf("en", "es", "hi", "fr", "de", "ja", "zh", "ru", "pt", "ar")
        val rawText = String(bytes, Charsets.ISO_8859_1)
        for (locale in candidateLocales) {
            val marker = "values-$locale"
            if (rawText.contains(marker)) {
                localizedMap[locale] = "Localized resource table entry ($locale)"
            }
        }
    }

    private fun parseStringPool(bytes: ByteArray, chunkStart: Int): List<String> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(chunkStart)
        val chunkType = buffer.short.toInt() and 0xFFFF
        val headerSize = buffer.short.toInt() and 0xFFFF
        val chunkSize = buffer.int
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int

        val isUtf8 = (flags and 0x0100) != 0
        if (stringCount <= 0 || stringCount > 500000) return emptyList()

        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            if (buffer.remaining() < 4) break
            offsets[i] = buffer.int
        }

        val poolBase = chunkStart + stringsStart
        val strings = mutableListOf<String>()

        for (i in 0 until stringCount) {
            val strOffset = poolBase + offsets[i]
            if (strOffset >= bytes.size) {
                strings.add("")
                continue
            }

            try {
                if (isUtf8) {
                    var p = strOffset
                    var len = bytes[p].toInt() and 0xFF
                    if ((len and 0x80) != 0) {
                        p++
                        len = ((len and 0x7F) shl 8) or (bytes[p].toInt() and 0xFF)
                    }
                    p++
                    var byteLen = bytes[p].toInt() and 0xFF
                    if ((byteLen and 0x80) != 0) {
                        p++
                        byteLen = ((byteLen and 0x7F) shl 8) or (bytes[p].toInt() and 0xFF)
                    }
                    p++
                    val actualLen = minOf(byteLen, bytes.size - p)
                    val str = if (actualLen > 0) String(bytes, p, actualLen, Charsets.UTF_8) else ""
                    strings.add(str)
                } else {
                    var p = strOffset
                    var charLen = (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8)
                    p += 2
                    val byteLen = charLen * 2
                    val actualLen = minOf(byteLen, bytes.size - p)
                    val str = if (actualLen > 0) String(bytes, p, actualLen, Charsets.UTF_16LE) else ""
                    strings.add(str)
                }
            } catch (e: Exception) {
                strings.add("")
            }
        }

        return strings
    }

    /**
     * Rewrites specific strings in resources.arsc's global string pool.
     */
    fun rewriteArscStrings(bytes: ByteArray, replacements: Map<String, String>): ByteArray {
        if (bytes.size < 40 || replacements.isEmpty()) return bytes
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val tableType = buffer.short.toInt() and 0xFFFF
        val tableHeaderSize = buffer.short.toInt() and 0xFFFF
        val originalTotalSize = buffer.int
        val packageCount = buffer.int

        if (tableType != 0x0002) return bytes

        val poolPos = buffer.position()
        val poolType = buffer.short.toInt() and 0xFFFF
        val poolHeaderSize = buffer.short.toInt() and 0xFFFF
        val poolChunkSize = buffer.int
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int

        if (poolType != 0x0001 || stringCount <= 0) return bytes

        val isUtf8 = (flags and 0x0100) != 0

        // Read original strings
        val originalStrings = parseStringPool(bytes, poolPos)
        val updatedStrings = originalStrings.map { original ->
            replacements[original] ?: original
        }

        // Re-encode
        val strDataStream = ByteArrayOutputStream()
        val offsetTable = IntArray(stringCount)

        for (i in 0 until stringCount) {
            offsetTable[i] = strDataStream.size()
            val str = updatedStrings[i]

            if (isUtf8) {
                val utf8Bytes = str.toByteArray(Charsets.UTF_8)
                val charLen = str.length
                val byteLen = utf8Bytes.size

                if (charLen > 0x7F) {
                    strDataStream.write((charLen shr 8) or 0x80)
                    strDataStream.write(charLen and 0xFF)
                } else {
                    strDataStream.write(charLen)
                }

                if (byteLen > 0x7F) {
                    strDataStream.write((byteLen shr 8) or 0x80)
                    strDataStream.write(byteLen and 0xFF)
                } else {
                    strDataStream.write(byteLen)
                }

                strDataStream.write(utf8Bytes)
                strDataStream.write(0)
            } else {
                val utf16Bytes = str.toByteArray(Charsets.UTF_16LE)
                val charLen = str.length

                strDataStream.write(charLen and 0xFF)
                strDataStream.write((charLen shr 8) and 0xFF)
                strDataStream.write(utf16Bytes)
                strDataStream.write(0)
                strDataStream.write(0)
            }
        }

        while (strDataStream.size() % 4 != 0) {
            strDataStream.write(0)
        }

        val newStringsData = strDataStream.toByteArray()
        val newStringsStart = 28 + (stringCount * 4) + (styleCount * 4)
        val newPoolChunkSize = newStringsStart + newStringsData.size

        val sizeDiff = newPoolChunkSize - poolChunkSize
        val newTotalSize = originalTotalSize + sizeDiff

        val out = ByteArrayOutputStream()
        val headerBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        headerBuf.putShort(tableType.toShort())
        headerBuf.putShort(tableHeaderSize.toShort())
        headerBuf.putInt(newTotalSize)
        headerBuf.putInt(packageCount)
        out.write(headerBuf.array())

        val poolHeaderBuf = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        poolHeaderBuf.putShort(poolType.toShort())
        poolHeaderBuf.putShort(poolHeaderSize.toShort())
        poolHeaderBuf.putInt(newPoolChunkSize)
        poolHeaderBuf.putInt(stringCount)
        poolHeaderBuf.putInt(styleCount)
        poolHeaderBuf.putInt(flags)
        poolHeaderBuf.putInt(newStringsStart)
        poolHeaderBuf.putInt(if (styleCount > 0) newStringsStart + newStringsData.size else 0)
        out.write(poolHeaderBuf.array())

        val offsetsBuf = ByteBuffer.allocate(stringCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (off in offsetTable) {
            offsetsBuf.putInt(off)
        }
        out.write(offsetsBuf.array())

        out.write(newStringsData)

        val restOffset = poolPos + poolChunkSize
        if (restOffset < bytes.size) {
            out.write(bytes, restOffset, bytes.size - restOffset)
        }

        return out.toByteArray()
    }
}
