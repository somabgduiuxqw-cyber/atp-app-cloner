package com.atp.cloner.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SampleApkGenerator(private val context: Context) {

    suspend fun createSampleApk(): File = withContext(Dispatchers.IO) {
        val sampleDir = File(context.filesDir, "sample_apks")
        if (!sampleDir.exists()) sampleDir.mkdirs()

        val apkFile = File(sampleDir, "ATP_Sample_App_v1.0.apk")
        if (apkFile.exists() && apkFile.length() > 5000) {
            return@withContext apkFile
        }

        val fos = FileOutputStream(apkFile)
        val zos = ZipOutputStream(fos)

        // 1. AndroidManifest.xml (Binary XML)
        val manifestBytes = buildSampleBinaryManifest(
            packageName = "com.atp.sampleapp",
            appName = "Sample App",
            launcherActivity = "com.atp.sampleapp.MainActivity",
            providerAuthority = "com.atp.sampleapp.fileprovider"
        )
        addZipEntry(zos, "AndroidManifest.xml", manifestBytes)

        // 2. resources.arsc (Compiled Resource Table)
        val arscBytes = buildSampleArsc(
            appName = "Sample App",
            localizedNameEs = "Aplicación Ejemplo",
            localizedNameHi = "नमूना ऐप"
        )
        addZipEntry(zos, "resources.arsc", arscBytes)

        // 3. classes.dex (DEX Bytecode)
        val dexBytes = buildSampleDex(
            packageName = "com.atp.sampleapp",
            className = "MainActivity"
        )
        addZipEntry(zos, "classes.dex", dexBytes)

        // 4. Launcher Icons (PNGs across 5 densities)
        val densities = mapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192
        )
        for ((density, size) in densities) {
            val iconBytes = createSampleIconPng(size, "ATP", Color.parseColor("#2563EB"))
            addZipEntry(zos, "res/mipmap-$density/ic_launcher.png", iconBytes)
        }

        // 5. Adaptive Icon XML
        val adaptiveXml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>""".trimIndent().toByteArray(Charsets.UTF_8)
        addZipEntry(zos, "res/mipmap-anydpi-v26/ic_launcher.xml", adaptiveXml)

        // 6. Foreground drawable
        val fgBytes = createSampleIconPng(144, "ATP", Color.TRANSPARENT)
        addZipEntry(zos, "res/mipmap-xxhdpi/ic_launcher_foreground.png", fgBytes)

        // 7. Assets file (JSON config)
        val assetJson = """{
  "client_id": "com.atp.sampleapp",
  "app_version": "1.0.0",
  "environment": "production",
  "build_suite": "ATP Network Discovery"
}""".trimIndent().toByteArray(Charsets.UTF_8)
        addZipEntry(zos, "assets/app_config.json", assetJson)

        // 8. Sign with basic v1 signature
        val manifestMf = """Manifest-Version: 1.0
Created-By: 1.0 (ATP Network Cloner Suite)

Name: AndroidManifest.xml
SHA-256-Digest: sampleDigest123456789=

Name: classes.dex
SHA-256-Digest: sampleDigest987654321=
""".toByteArray(Charsets.UTF_8)
        addZipEntry(zos, "META-INF/MANIFEST.MF", manifestMf)

        val certSf = """Signature-Version: 1.0
Created-By: 1.0 (ATP Network Cloner Suite)
SHA-256-Digest-Manifest: sampleManifestDigest=

Name: AndroidManifest.xml
SHA-256-Digest: sampleDigest123456789=
""".toByteArray(Charsets.UTF_8)
        addZipEntry(zos, "META-INF/CERT.SF", certSf)

        val certRsa = ByteArray(256) { 0x42 } // simulated signature block
        addZipEntry(zos, "META-INF/CERT.RSA", certRsa)

        zos.close()
        fos.close()

        apkFile
    }

    private fun addZipEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun createSampleIconPng(size: Int, text: String, bgColor: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (bgColor != Color.TRANSPARENT) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColor
            }
            canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.22f, size * 0.22f, bgPaint)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.38f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val xPos = size / 2f
        val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(text, xPos, yPos, textPaint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun buildSampleBinaryManifest(
        packageName: String,
        appName: String,
        launcherActivity: String,
        providerAuthority: String
    ): ByteArray {
        // Construct a standard, well-formed binary XML chunk
        val strings = listOf(
            packageName,
            appName,
            launcherActivity,
            providerAuthority,
            "manifest",
            "application",
            "activity",
            "provider",
            "intent-filter",
            "action",
            "category",
            "package",
            "label",
            "icon",
            "name",
            "authorities",
            "exported",
            "versionName",
            "versionCode",
            "1.0.0",
            "1",
            "android.intent.action.MAIN",
            "android.intent.category.LAUNCHER",
            "@mipmap/ic_launcher",
            "@string/app_name"
        )

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
        while (strDataStream.size() % 4 != 0) {
            strDataStream.write(0)
        }

        val strData = strDataStream.toByteArray()
        val stringsStart = 28 + (strings.size * 4)
        val poolChunkSize = stringsStart + strData.size

        val totalSize = 8 + poolChunkSize + 128 // header + pool + minimal tags

        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x00080003) // CHUNK_AXML_FILE
        header.putInt(totalSize)
        out.write(header.array())

        val poolHeader = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        poolHeader.putInt(0x00010001) // CHUNK_STRING_POOL
        poolHeader.putInt(poolChunkSize)
        poolHeader.putInt(strings.size)
        poolHeader.putInt(0) // style count
        poolHeader.putInt(0x00000100) // UTF-8
        poolHeader.putInt(stringsStart)
        poolHeader.putInt(0) // styles start
        out.write(poolHeader.array())

        val offsetsBuf = ByteBuffer.allocate(strings.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (off in offsets) offsetsBuf.putInt(off)
        out.write(offsetsBuf.array())
        out.write(strData)

        // Minimal filler XML chunks
        val dummyXml = ByteArray(128) { 0 }
        out.write(dummyXml)

        return out.toByteArray()
    }

    private fun buildSampleArsc(
        appName: String,
        localizedNameEs: String,
        localizedNameHi: String
    ): ByteArray {
        val strings = listOf(
            "app_name",
            appName,
            "values-es",
            localizedNameEs,
            "values-hi",
            localizedNameHi,
            "res/mipmap-hdpi/ic_launcher.png",
            "res/mipmap-xxhdpi/ic_launcher.png",
            "res/mipmap-anydpi-v26/ic_launcher.xml"
        )

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
        val totalSize = 12 + poolChunkSize + 64

        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0x0002.toShort()) // RES_TABLE_TYPE
        header.putShort(12.toShort())
        header.putInt(totalSize)
        header.putInt(1) // packageCount
        out.write(header.array())

        val poolHeader = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        poolHeader.putShort(0x0001.toShort()) // RES_STRING_POOL_TYPE
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

        out.write(ByteArray(64) { 0 })
        return out.toByteArray()
    }

    private fun buildSampleDex(packageName: String, className: String): ByteArray {
        val slashPkg = packageName.replace('.', '/')
        val classDesc = "L$slashPkg/$className;"

        val strings = listOf(
            packageName,
            slashPkg,
            classDesc,
            "Ljava/lang/Object;",
            "onCreate",
            "V",
            "()V"
        )

        val out = ByteArrayOutputStream()
        val strDataStream = ByteArrayOutputStream()
        val strOffsets = IntArray(strings.size)

        for (i in strings.indices) {
            strOffsets[i] = strDataStream.size()
            val utf8 = strings[i].toByteArray(Charsets.UTF_8)
            strDataStream.write(utf8.size) // ULEB128 len
            strDataStream.write(utf8)
            strDataStream.write(0) // null
        }

        val header = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN)
        header.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
        header.putInt(0x12345678) // checksum
        header.put(ByteArray(20) { 0 }) // signature
        val strIdsOff = 112
        val strDataOff = strIdsOff + (strings.size * 4)
        val totalSize = strDataOff + strDataStream.size()
        header.putInt(totalSize) // file size
        header.putInt(112) // header size
        header.putInt(0x12345678) // endian tag
        header.putInt(0) // link size
        header.putInt(0) // link off
        header.putInt(0) // map off
        header.putInt(strings.size) // string_ids_size
        header.putInt(strIdsOff) // string_ids_off
        header.putInt(1) // type_ids_size
        header.putInt(0) // type_ids_off
        header.putInt(0) // proto_ids_size
        header.putInt(0) // proto_ids_off
        header.putInt(0) // field_ids_size
        header.putInt(0) // field_ids_off
        header.putInt(1) // method_ids_size
        header.putInt(0) // method_ids_off
        header.putInt(1) // class_defs_size
        header.putInt(0) // class_defs_off
        header.putInt(0) // data_size
        header.putInt(0) // data_off

        out.write(header.array())

        val strIdsBuf = ByteBuffer.allocate(strings.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (off in strOffsets) {
            strIdsBuf.putInt(strDataOff + off)
        }
        out.write(strIdsBuf.array())
        out.write(strDataStream.toByteArray())

        return out.toByteArray()
    }
}
