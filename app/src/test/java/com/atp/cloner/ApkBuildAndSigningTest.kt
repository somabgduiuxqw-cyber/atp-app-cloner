package com.atp.cloner

import com.atp.cloner.engine.build.ApkAlignmentService
import com.atp.cloner.engine.build.ApkDexValidator
import com.atp.cloner.engine.build.ApkManifestCompiler
import com.atp.cloner.engine.build.ApkResourceRebuilder
import com.atp.cloner.engine.build.ApkSignatureVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkBuildAndSigningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDexValidatorDetectsValidDexHeader() {
        val validator = ApkDexValidator()

        // Construct a synthetic 0x70 header DEX file
        val dexBytes = ByteArray(0x78)
        // Magic: dex\n035\0
        val magic = byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00)
        System.arraycopy(magic, 0, dexBytes, 0, magic.size)

        // Endian tag: 0x12345678 at offset 40
        ByteBuffer.wrap(dexBytes, 40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x12345678)
        // Header size: 0x70 at offset 36
        ByteBuffer.wrap(dexBytes, 36, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x70)
        // File size: 0x78 at offset 32
        ByteBuffer.wrap(dexBytes, 32, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x78)

        // SHA-1 signature at offset 12..31 (20 bytes calculated over offset 32..EOF)
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(dexBytes, 32, dexBytes.size - 32)
        val sig = sha1.digest()
        System.arraycopy(sig, 0, dexBytes, 12, 20)

        // Adler32 checksum at offset 8..11 (calculated over offset 12..EOF)
        val adler = Adler32()
        adler.update(dexBytes, 12, dexBytes.size - 12)
        ByteBuffer.wrap(dexBytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(adler.value.toInt())

        val info = validator.validateDexBytes("classes.dex", dexBytes)
        assertTrue("DEX magic must match", info.magic.contains("dex"))
        assertTrue("Adler32 checksum must be valid", info.adler32Valid)
        assertTrue("SHA-1 signature must be valid", info.sha1Valid)
    }

    @Test
    fun testDexValidatorRejectsCorruptHeader() {
        val validator = ApkDexValidator()
        val corruptBytes = byteArrayOf(1, 2, 3, 4)
        val info = validator.validateDexBytes("classes.dex", corruptBytes)
        assertFalse(info.adler32Valid)
        assertFalse(info.sha1Valid)
    }

    @Test
    fun testManifestCompilerValidatesAxmlChunkHeader() {
        val compiler = ApkManifestCompiler()

        // Valid chunk header for RES_XML_TYPE (0x0003, headerSize = 8, chunkSize = 16)
        val validHeader = ByteArray(16)
        val buf = ByteBuffer.wrap(validHeader).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0003.toShort()) // type
        buf.putShort(8.toShort())      // header size
        buf.putInt(16)                 // chunk size

        val result = compiler.compileAndValidate(validHeader)
        assertTrue("Should accept valid RES_XML_TYPE header", result.isValid)

        // Invalid chunk header type
        val invalidHeader = ByteArray(16)
        val invBuf = ByteBuffer.wrap(invalidHeader).order(ByteOrder.LITTLE_ENDIAN)
        invBuf.putShort(0x0099.toShort()) // invalid
        invBuf.putShort(8.toShort())
        invBuf.putInt(16)

        val invalidResult = compiler.compileAndValidate(invalidHeader)
        assertFalse("Should reject invalid XML type", invalidResult.isValid)
    }

    @Test
    fun testResourceRebuilderValidatesArscStructure() {
        val rebuilder = ApkResourceRebuilder()

        // Valid RES_TABLE_TYPE (0x0002, headerSize = 12, chunkSize = 16, packageCount = 1)
        val validTable = ByteArray(16)
        val buf = ByteBuffer.wrap(validTable).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0002.toShort()) // RES_TABLE_TYPE
        buf.putShort(12.toShort())     // header size
        buf.putInt(16)                 // chunk size
        buf.putInt(1)                  // packageCount

        val result = rebuilder.rebuildAndValidate(validTable)
        assertTrue(result.isValid)
        assertEquals(1, result.packageCount)
        assertTrue(result.is4ByteAligned)
    }

    @Test
    fun testZipAlignmentAlignsStoredEntriesTo4Bytes() {
        val aligner = ApkAlignmentService()
        val inputZip = tempFolder.newFile("unaligned.apk")
        val outputZip = tempFolder.newFile("aligned.apk")

        ZipOutputStream(FileOutputStream(inputZip)).use { zos ->
            val entry = ZipEntry("resources.arsc")
            val content = "Sample Resource Table Payload".toByteArray(Charsets.UTF_8)
            entry.method = ZipEntry.STORED
            entry.size = content.size.toLong()
            entry.compressedSize = content.size.toLong()
            val crc = CRC32()
            crc.update(content)
            entry.crc = crc.value

            zos.putNextEntry(entry)
            zos.write(content)
            zos.closeEntry()

            // Deflated entry
            val defEntry = ZipEntry("assets/config.json")
            defEntry.method = ZipEntry.DEFLATED
            zos.putNextEntry(defEntry)
            zos.write("{}".toByteArray())
            zos.closeEntry()
        }

        val result = aligner.alignApk(inputZip, outputZip)
        assertTrue("Alignment must report success", result.success)
        assertTrue("Must have aligned at least 1 stored entry", result.alignedEntriesCount >= 1)
        assertTrue("Output aligned file must exist", outputZip.exists())
    }

    @Test
    fun testSignatureVerifierDetectsMissingMetaInf() {
        val verifier = ApkSignatureVerifier()
        val unsignedZip = tempFolder.newFile("unsigned.apk")

        ZipOutputStream(FileOutputStream(unsignedZip)).use { zos ->
            val entry = ZipEntry("classes.dex")
            zos.putNextEntry(entry)
            zos.write("dex_content".toByteArray())
            zos.closeEntry()
        }

        val result = verifier.verifyApkSignature(unsignedZip)
        assertFalse("Unsigned APK must not verify", result.isValid)
        assertFalse("Must report missing MANIFEST.MF", result.hasManifestMf)
    }
}
