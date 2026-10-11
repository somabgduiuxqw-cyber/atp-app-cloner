package com.atp.cloner

import com.atp.cloner.engine.AXmlDecoder
import com.atp.cloner.engine.ArscDecoder
import com.atp.cloner.engine.DexDiscoveryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ApkDiscoveryTest {

    @Test
    fun testDexParserIdentifiesClassDescriptors() {
        val parser = DexDiscoveryParser()
        val packageName = "com.atp.testapp"
        val slashPkg = packageName.replace('.', '/')
        val classDesc = "L$slashPkg/MainActivity;"

        val strings = listOf(packageName, slashPkg, classDesc, "Ljava/lang/Object;")

        val out = ByteArrayOutputStream()
        val strDataStream = ByteArrayOutputStream()
        val strOffsets = IntArray(strings.size)

        for (i in strings.indices) {
            strOffsets[i] = strDataStream.size()
            val utf8 = strings[i].toByteArray(Charsets.UTF_8)
            strDataStream.write(utf8.size)
            strDataStream.write(utf8)
            strDataStream.write(0)
        }

        val header = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN)
        header.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
        header.putInt(0x12345678)
        header.put(ByteArray(20) { 0 })
        val strIdsOff = 112
        val strDataOff = strIdsOff + (strings.size * 4)
        val totalSize = strDataOff + strDataStream.size()
        header.putInt(totalSize)
        header.putInt(112)
        header.putInt(0x12345678)
        header.putInt(0)
        header.putInt(0)
        header.putInt(0)
        header.putInt(strings.size)
        header.putInt(strIdsOff)
        header.putInt(1)
        header.putInt(0)
        header.putInt(0)
        header.putInt(0)
        header.putInt(0)
        header.putInt(0)
        header.putInt(1)
        header.putInt(0)
        header.putInt(1)
        header.putInt(0)
        header.putInt(0)
        header.putInt(0)
        out.write(header.array())

        val strIdsBuf = ByteBuffer.allocate(strings.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (off in strOffsets) strIdsBuf.putInt(strDataOff + off)
        out.write(strIdsBuf.array())
        out.write(strDataStream.toByteArray())

        val dexBytes = out.toByteArray()
        val result = parser.scanDex(dexBytes, "classes.dex", packageName)

        assertTrue("Should detect at least 1 reference", result.references.isNotEmpty())
        val classDescRef = result.references.find { it.referenceType == com.atp.cloner.data.model.DexReferenceType.CLASS_DESCRIPTOR }
        assertNotNull("Should find confirmed class descriptor", classDescRef)
        assertEquals(classDesc, classDescRef?.matchedValue)
    }

    @Test
    fun testArscDecoderParsesGlobalStrings() {
        val decoder = ArscDecoder()
        val strings = listOf("app_name", "Test Application", "values-es", "Aplicación de Prueba")

        val strDataStream = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)

        for (i in strings.indices) {
            offsets[i] = strDataStream.size()
            val utf8 = strings[i].toByteArray(Charsets.UTF_8)
            strDataStream.write(strings[i].length)
            strDataStream.write(utf8.size)
            strDataStream.write(utf8)
            strDataStream.write(0)
        }
        while (strDataStream.size() % 4 != 0) strDataStream.write(0)

        val strData = strDataStream.toByteArray()
        val stringsStart = 28 + (strings.size * 4)
        val poolChunkSize = stringsStart + strData.size
        val totalSize = 12 + poolChunkSize + 32

        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0x0002.toShort())
        header.putShort(12.toShort())
        header.putInt(totalSize)
        header.putInt(1)
        out.write(header.array())

        val poolHeader = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        poolHeader.putShort(0x0001.toShort())
        poolHeader.putShort(28.toShort())
        poolHeader.putInt(poolChunkSize)
        poolHeader.putInt(strings.size)
        poolHeader.putInt(0)
        poolHeader.putInt(0x00000100) // UTF-8
        poolHeader.putInt(stringsStart)
        poolHeader.putInt(0)
        out.write(poolHeader.array())

        val offsetsBuf = ByteBuffer.allocate(strings.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (off in offsets) offsetsBuf.putInt(off)
        out.write(offsetsBuf.array())
        out.write(strData)
        out.write(ByteArray(32) { 0 })

        val arscBytes = out.toByteArray()
        val arscData = decoder.decodeArsc(arscBytes)

        assertTrue(arscData.globalStrings.contains("app_name"))
        assertTrue(arscData.globalStrings.contains("Test Application"))
    }
}
