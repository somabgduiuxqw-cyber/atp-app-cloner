package com.atp.cloner.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AXmlDecoder {

    data class ManifestData(
        val packageName: String = "",
        val appLabel: String = "",
        val appLabelResId: Int? = null,
        val appIcon: String = "",
        val appIconResId: Int? = null,
        val appRoundIcon: String = "",
        val appRoundIconResId: Int? = null,
        val versionName: String = "",
        val versionCode: Long = 0,
        val minSdk: Int = 21,
        val targetSdk: Int = 33,
        val activities: List<ActivityData> = emptyList(),
        val providers: List<ProviderData> = emptyList(),
        val stringPool: List<String> = emptyList()
    )

    data class ActivityData(
        val name: String,
        val label: String? = null,
        val labelResId: Int? = null,
        val icon: String? = null,
        val isLauncher: Boolean = false,
        val isAlias: Boolean = false,
        val targetActivity: String? = null
    )

    data class ProviderData(
        val name: String,
        val authorities: String,
        val isExported: Boolean = false
    )

    companion object {
        private const val CHUNK_AXML_FILE = 0x00080003
        private const val CHUNK_STRING_POOL = 0x00010001
        private const val CHUNK_RESOURCE_IDS = 0x00080180
        private const val CHUNK_XML_START_TAG = 0x00100102
        private const val CHUNK_XML_END_TAG = 0x00100103
        private const val CHUNK_XML_TEXT = 0x00100104

        private const val TYPE_STRING = 0x03
        private const val TYPE_REFERENCE = 0x01
    }

    fun decodeManifest(bytes: ByteArray): ManifestData {
        if (bytes.size < 8) return ManifestData()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val fileChunkType = buffer.int
        if (fileChunkType != CHUNK_AXML_FILE && (fileChunkType and 0xFFFF) != 0x0003) {
            return fallbackDecode(bytes)
        }
        val fileSize = buffer.int

        var stringPool: List<String> = emptyList()
        var packageName = ""
        var appLabel = ""
        var appLabelResId: Int? = null
        var appIcon = ""
        var appIconResId: Int? = null
        var appRoundIcon = ""
        var appRoundIconResId: Int? = null
        var versionName = ""
        var versionCode = 0L
        var minSdk = 21
        var targetSdk = 33

        val activities = mutableListOf<ActivityData>()
        val providers = mutableListOf<ProviderData>()

        var currentActivity: ActivityData? = null
        var currentIsLauncher = false

        while (buffer.hasRemaining() && buffer.position() < bytes.size - 4) {
            val chunkStart = buffer.position()
            val chunkType = buffer.int
            val chunkSize = buffer.int
            if (chunkSize <= 0 || chunkStart + chunkSize > bytes.size) break

            when (chunkType) {
                CHUNK_STRING_POOL, 0x0001 -> {
                    stringPool = parseStringPool(bytes, chunkStart)
                    buffer.position(chunkStart + chunkSize)
                }
                CHUNK_XML_START_TAG, 0x0102 -> {
                    val lineNum = buffer.int
                    val commentIdx = buffer.int
                    val nsIdx = buffer.int
                    val nameIdx = buffer.int
                    val attrStart = buffer.short
                    val attrSize = buffer.short
                    val attrCount = buffer.short.toInt() and 0xFFFF
                    val idIdx = buffer.short
                    val classIdx = buffer.short
                    val styleIdx = buffer.short

                    val tagName = getString(stringPool, nameIdx)

                    val attributes = mutableMapOf<String, Pair<String, Int?>>()

                    for (i in 0 until attrCount) {
                        val aNsIdx = buffer.int
                        val aNameIdx = buffer.int
                        val aRawValIdx = buffer.int
                        val aValSize = buffer.short
                        val aValRes0 = buffer.get()
                        val aValType = buffer.get().toInt() and 0xFF
                        val aValData = buffer.int

                        val aName = getString(stringPool, aNameIdx)
                        val aRawVal = getString(stringPool, aRawValIdx)

                        val resolvedVal = if (aRawVal.isNotEmpty()) {
                            aRawVal
                        } else if (aValType == TYPE_REFERENCE) {
                            "@0x${Integer.toHexString(aValData)}"
                        } else if (aValType == 0x10) { // integer
                            aValData.toString()
                        } else {
                            ""
                        }

                        val resId = if (aValType == TYPE_REFERENCE) aValData else null
                        attributes[aName] = Pair(resolvedVal, resId)
                    }

                    when (tagName) {
                        "manifest" -> {
                            packageName = attributes["package"]?.first ?: ""
                            versionName = attributes["versionName"]?.first ?: ""
                            versionCode = attributes["versionCode"]?.first?.toLongOrNull() ?: 0L
                        }
                        "application" -> {
                            attributes["label"]?.let {
                                appLabel = it.first
                                appLabelResId = it.second
                            }
                            attributes["icon"]?.let {
                                appIcon = it.first
                                appIconResId = it.second
                            }
                            attributes["roundIcon"]?.let {
                                appRoundIcon = it.first
                                appRoundIconResId = it.second
                            }
                        }
                        "activity", "activity-alias" -> {
                            val actName = attributes["name"]?.first ?: attributes["targetActivity"]?.first ?: "Unknown"
                            currentActivity = ActivityData(
                                name = actName,
                                label = attributes["label"]?.first,
                                labelResId = attributes["label"]?.second,
                                icon = attributes["icon"]?.first,
                                isAlias = tagName == "activity-alias",
                                targetActivity = attributes["targetActivity"]?.first
                            )
                            currentIsLauncher = false
                        }
                        "action" -> {
                            val actName = attributes["name"]?.first
                            if (actName == "android.intent.action.MAIN") {
                                currentIsLauncher = true
                            }
                        }
                        "category" -> {
                            val catName = attributes["name"]?.first
                            if (catName == "android.intent.category.LAUNCHER" && currentIsLauncher && currentActivity != null) {
                                currentActivity = currentActivity!!.copy(isLauncher = true)
                            }
                        }
                        "provider" -> {
                            val pName = attributes["name"]?.first ?: ""
                            val pAuth = attributes["authorities"]?.first ?: ""
                            val pExported = attributes["exported"]?.first == "true"
                            if (pName.isNotEmpty() || pAuth.isNotEmpty()) {
                                providers.add(ProviderData(pName, pAuth, pExported))
                            }
                        }
                        "uses-sdk" -> {
                            minSdk = attributes["minSdkVersion"]?.first?.toIntOrNull() ?: minSdk
                            targetSdk = attributes["targetSdkVersion"]?.first?.toIntOrNull() ?: targetSdk
                        }
                    }

                    buffer.position(chunkStart + chunkSize)
                }
                CHUNK_XML_END_TAG, 0x0103 -> {
                    buffer.position(chunkStart + 16)
                    val nameIdx = buffer.int
                    val tagName = getString(stringPool, nameIdx)
                    if ((tagName == "activity" || tagName == "activity-alias") && currentActivity != null) {
                        activities.add(currentActivity!!)
                        currentActivity = null
                    }
                    buffer.position(chunkStart + chunkSize)
                }
                else -> {
                    buffer.position(chunkStart + chunkSize)
                }
            }
        }

        return ManifestData(
            packageName = packageName,
            appLabel = appLabel,
            appLabelResId = appLabelResId,
            appIcon = appIcon,
            appIconResId = appIconResId,
            appRoundIcon = appRoundIcon,
            appRoundIconResId = appRoundIconResId,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            activities = activities,
            providers = providers,
            stringPool = stringPool
        )
    }

    private fun parseStringPool(bytes: ByteArray, chunkStart: Int): List<String> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(chunkStart)
        val chunkType = buffer.int
        val chunkSize = buffer.int
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int

        val isUtf8 = (flags and 0x0100) != 0

        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
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
                    if ((charLen and 0x8000) != 0) {
                        val high = charLen and 0x7FFF
                        val low = (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8)
                        charLen = (high shl 16) or low
                        p += 2
                    }
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

    private fun getString(pool: List<String>, idx: Int): String {
        return if (idx in pool.indices) pool[idx] else ""
    }

    private fun fallbackDecode(bytes: ByteArray): ManifestData {
        // String scan fallback for unconventional or obfuscated manifests
        val text = String(bytes, Charsets.ISO_8859_1)
        var pkg = ""
        val pkgIdx = text.indexOf("package")
        if (pkgIdx != -1) {
            val sub = text.substring(pkgIdx, minOf(pkgIdx + 100, text.length))
            val match = Regex("""([a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+)""").find(sub)
            if (match != null) pkg = match.value
        }
        return ManifestData(packageName = pkg)
    }

    /**
     * Modifies the Binary XML by updating specific strings in the string pool
     * while preserving all structure, attributes, and resource IDs.
     */
    fun rewriteManifestStrings(
        bytes: ByteArray,
        replacements: Map<String, String>
    ): ByteArray {
        if (bytes.size < 36 || replacements.isEmpty()) return bytes
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val fileChunkType = buffer.int
        val originalTotalSize = buffer.int

        // Locate string pool
        val poolPos = buffer.position()
        val poolType = buffer.int
        val poolChunkSize = buffer.int
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int

        val isUtf8 = (flags and 0x0100) != 0

        // Parse existing strings
        val originalStrings = parseStringPool(bytes, poolPos)
        val updatedStrings = originalStrings.map { original ->
            replacements[original] ?: original
        }

        // Re-encode string pool data
        val strDataStream = ByteArrayOutputStream()
        val offsetTable = IntArray(stringCount)

        for (i in 0 until stringCount) {
            offsetTable[i] = strDataStream.size()
            val str = updatedStrings[i]

            if (isUtf8) {
                val utf8Bytes = str.toByteArray(Charsets.UTF_8)
                val charLen = str.length
                val byteLen = utf8Bytes.size

                // write char len
                if (charLen > 0x7F) {
                    strDataStream.write((charLen shr 8) or 0x80)
                    strDataStream.write(charLen and 0xFF)
                } else {
                    strDataStream.write(charLen)
                }

                // write byte len
                if (byteLen > 0x7F) {
                    strDataStream.write((byteLen shr 8) or 0x80)
                    strDataStream.write(byteLen and 0xFF)
                } else {
                    strDataStream.write(byteLen)
                }

                strDataStream.write(utf8Bytes)
                strDataStream.write(0) // null terminator
            } else {
                val utf16Bytes = str.toByteArray(Charsets.UTF_16LE)
                val charLen = str.length

                if (charLen > 0x7FFF) {
                    val high = (charLen shr 16) or 0x8000
                    strDataStream.write(high and 0xFF)
                    strDataStream.write((high shr 8) and 0xFF)
                    strDataStream.write(charLen and 0xFF)
                    strDataStream.write((charLen shr 8) and 0xFF)
                } else {
                    strDataStream.write(charLen and 0xFF)
                    strDataStream.write((charLen shr 8) and 0xFF)
                }

                strDataStream.write(utf16Bytes)
                strDataStream.write(0) // null terminator (2 bytes)
                strDataStream.write(0)
            }
        }

        // Pad string data to 4-byte boundary
        while (strDataStream.size() % 4 != 0) {
            strDataStream.write(0)
        }

        val newStringsData = strDataStream.toByteArray()
        val newStringsStart = 28 + (stringCount * 4) + (styleCount * 4)
        val newPoolChunkSize = newStringsStart + newStringsData.size

        // Calculate size difference
        val sizeDiff = newPoolChunkSize - poolChunkSize
        val newTotalSize = originalTotalSize + sizeDiff

        // Construct new AXML byte array
        val out = ByteArrayOutputStream()
        val headerBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        headerBuf.putInt(fileChunkType)
        headerBuf.putInt(newTotalSize)
        out.write(headerBuf.array())

        // String pool header
        val poolHeaderBuf = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        poolHeaderBuf.putInt(poolType)
        poolHeaderBuf.putInt(newPoolChunkSize)
        poolHeaderBuf.putInt(stringCount)
        poolHeaderBuf.putInt(styleCount)
        poolHeaderBuf.putInt(flags)
        poolHeaderBuf.putInt(newStringsStart)
        poolHeaderBuf.putInt(if (styleCount > 0) newStringsStart + newStringsData.size else 0)
        out.write(poolHeaderBuf.array())

        // Offset table
        val offsetsBuf = ByteBuffer.allocate(stringCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (off in offsetTable) {
            offsetsBuf.putInt(off)
        }
        out.write(offsetsBuf.array())

        // String data
        out.write(newStringsData)

        // Rest of the file after the original string pool
        val restOffset = poolPos + poolChunkSize
        if (restOffset < bytes.size) {
            out.write(bytes, restOffset, bytes.size - restOffset)
        }

        return out.toByteArray()
    }
}
