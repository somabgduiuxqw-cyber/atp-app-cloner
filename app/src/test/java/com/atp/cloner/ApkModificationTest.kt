package com.atp.cloner

import com.atp.cloner.data.model.CloneConfiguration
import com.atp.cloner.engine.AXmlDecoder
import com.atp.cloner.engine.ArscDecoder
import com.atp.cloner.engine.modification.ApkModificationPlanner
import com.atp.cloner.engine.modification.CloneConfigurationValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ApkModificationTest {

    @Test
    fun testConfigurationValidatorRejectsEmptyNameWhenEnabled() {
        val config = CloneConfiguration(
            changeAppName = true,
            newAppName = "   ",
            changePackageId = false,
            changeLauncherIcon = false
        )
        val result = CloneConfigurationValidator.validate(config, "Original App", "com.example.original")
        assertFalse(result.isValid)
        assertNotNull(result.nameError)
    }

    @Test
    fun testConfigurationValidatorValidatesPackageSyntax() {
        // Invalid syntax: no dot
        val invalidConfig = CloneConfiguration(
            changeAppName = false,
            changePackageId = true,
            newPackageId = "invalidpackage",
            changeLauncherIcon = false
        )
        val resultInvalid = CloneConfigurationValidator.validate(invalidConfig, "Original", "com.original")
        assertFalse(resultInvalid.isValid)
        assertNotNull(resultInvalid.packageError)

        // Invalid: collision with cloner package ID
        val collisionConfig = CloneConfiguration(
            changeAppName = false,
            changePackageId = true,
            newPackageId = "com.atp.cloner",
            changeLauncherIcon = false
        )
        val resultCollision = CloneConfigurationValidator.validate(collisionConfig, "Original", "com.original")
        assertFalse(resultCollision.isValid)
        assertTrue(resultCollision.packageError?.contains("reserved") == true)

        // Valid package
        val validConfig = CloneConfiguration(
            changeAppName = false,
            changePackageId = true,
            newPackageId = "com.mygreen.app",
            changeLauncherIcon = false
        )
        val resultValid = CloneConfigurationValidator.validate(validConfig, "Original", "com.original")
        assertTrue(resultValid.isValid)
    }

    @Test
    fun testConfigurationValidatorDetectsNoOp() {
        val noOpConfig = CloneConfiguration(
            changeAppName = false,
            changePackageId = false,
            changeLauncherIcon = false
        )
        val result = CloneConfigurationValidator.validate(noOpConfig, "Original", "com.original")
        assertFalse(result.isValid)
        assertTrue(result.isNoOp)
    }

    @Test
    fun testAXmlDecoderRewritesManifestStrings() {
        val decoder = AXmlDecoder()

        val originalPkg = "com.atp.sampleapp"
        val newPkg = "com.atp.sampleapp.cloned"
        val originalLabel = "Sample App"
        val newLabel = "Custom Clone App"

        val strings = listOf(
            originalPkg,
            originalLabel,
            "manifest",
            "application",
            "package",
            "label"
        )

        // Build sample binary XML
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
        val totalSize = 8 + poolChunkSize + 32

        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x00080003)
        header.putInt(totalSize)
        out.write(header.array())

        val poolHeader = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        poolHeader.putInt(0x00010001)
        poolHeader.putInt(poolChunkSize)
        poolHeader.putInt(strings.size)
        poolHeader.putInt(0)
        poolHeader.putInt(0x00000100)
        poolHeader.putInt(stringsStart)
        poolHeader.putInt(0)
        out.write(poolHeader.array())

        val offsetsBuf = ByteBuffer.allocate(strings.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (off in offsets) offsetsBuf.putInt(off)
        out.write(offsetsBuf.array())
        out.write(strData)
        out.write(ByteArray(32) { 0 })

        val originalAxmlBytes = out.toByteArray()

        // Apply replacement
        val replacements = mapOf(
            originalPkg to newPkg,
            originalLabel to newLabel
        )
        val modifiedBytes = decoder.rewriteManifestStrings(originalAxmlBytes, replacements)

        assertNotNull(modifiedBytes)
        assertTrue(modifiedBytes.isNotEmpty())

        val decoded = decoder.decodeManifest(modifiedBytes)
        assertTrue("String pool should contain new package", decoded.stringPool.contains(newPkg))
        assertTrue("String pool should contain new label", decoded.stringPool.contains(newLabel))
        assertFalse("Old package should be replaced", decoded.stringPool.contains(originalPkg))
    }

    @Test
    fun testArscDecoderRewritesStringPool() {
        val decoder = ArscDecoder()
        val originalName = "Original App Name"
        val newName = "Renamed Clone App"

        val strings = listOf("app_name", originalName, "values-es")

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
        val totalSize = 12 + poolChunkSize + 16

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
        poolHeader.putInt(0x00000100)
        poolHeader.putInt(stringsStart)
        poolHeader.putInt(0)
        out.write(poolHeader.array())

        val offsetsBuf = ByteBuffer.allocate(strings.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (off in offsets) offsetsBuf.putInt(off)
        out.write(offsetsBuf.array())
        out.write(strData)
        out.write(ByteArray(16) { 0 })

        val originalArsc = out.toByteArray()
        val modifiedArsc = decoder.rewriteArscStrings(originalArsc, mapOf(originalName to newName))

        val decodedModified = decoder.decodeArsc(modifiedArsc)
        assertTrue(decodedModified.globalStrings.contains(newName))
        assertFalse(decodedModified.globalStrings.contains(originalName))
    }
}
