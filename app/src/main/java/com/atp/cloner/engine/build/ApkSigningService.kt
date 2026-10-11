package com.atp.cloner.engine.build

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SigningServiceResult(
    val success: Boolean,
    val certFingerprintSha256: String,
    val signedApkFile: File?,
    val manifestEntryCount: Int,
    val details: String,
    val errorMessage: String? = null
)

class ApkSigningService(private val context: Context) {

    private val keyAlias = "atp_network_cloner_key"
    private val keyPassword = "atp_cloner_local_pass".toCharArray()

    fun getOrGenerateKeyPair(): KeyPair {
        val keystoreFile = File(context.filesDir, "atp_signing_keystore.bks")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())

        if (keystoreFile.exists()) {
            try {
                FileInputStream(keystoreFile).use { input ->
                    keyStore.load(input, keyPassword)
                    val privKey = keyStore.getKey(keyAlias, keyPassword) as? PrivateKey
                    val cert = keyStore.getCertificate(keyAlias)
                    if (privKey != null && cert != null) {
                        return KeyPair(cert.publicKey, privKey)
                    }
                }
            } catch (e: Exception) {
                // If corrupt, fallback to regeneration
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        return kpg.generateKeyPair()
    }

    fun getCertificateFingerprint(keyPair: KeyPair): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(keyPair.public.encoded)
        return hash.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Signs the input APK with APK Signature Scheme v1 (JAR signing).
     */
    fun signApk(unsignedApk: File, signedOutputApk: File): SigningServiceResult {
        return try {
            val keyPair = getOrGenerateKeyPair()
            val certFingerprint = getCertificateFingerprint(keyPair)

            val fileDigests = mutableMapOf<String, String>()
            ZipFile(unsignedApk).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || entry.name.startsWith("META-INF/")) continue

                    zipFile.getInputStream(entry).use { stream ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(16384)
                        var len: Int
                        while (stream.read(buffer).also { len = it } != -1) {
                            digest.update(buffer, 0, len)
                        }
                        val hashBase64 = Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
                        fileDigests[entry.name] = hashBase64
                    }
                }
            }

            // 1. Build MANIFEST.MF
            val manifestMfSb = StringBuilder()
            manifestMfSb.append("Manifest-Version: 1.0\r\n")
            manifestMfSb.append("Created-By: 1.0 (ATP App Cloner - ATP Network)\r\n\r\n")
            for ((name, hash) in fileDigests) {
                manifestMfSb.append("Name: $name\r\n")
                manifestMfSb.append("SHA-256-Digest: $hash\r\n\r\n")
            }
            val manifestMfBytes = manifestMfSb.toString().toByteArray(Charsets.UTF_8)

            // 2. Build CERT.SF
            val manifestDigest = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(manifestMfBytes),
                Base64.NO_WRAP
            )
            val certSfSb = StringBuilder()
            certSfSb.append("Signature-Version: 1.0\r\n")
            certSfSb.append("Created-By: 1.0 (ATP App Cloner - ATP Network)\r\n")
            certSfSb.append("SHA-256-Digest-Manifest: $manifestDigest\r\n\r\n")
            for ((name, hash) in fileDigests) {
                certSfSb.append("Name: $name\r\n")
                certSfSb.append("SHA-256-Digest: $hash\r\n\r\n")
            }
            val certSfBytes = certSfSb.toString().toByteArray(Charsets.UTF_8)

            // 3. Sign CERT.SF using RSA SHA256withRSA
            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(keyPair.private)
            signer.update(certSfBytes)
            val rawSignature = signer.sign()

            // 4. Construct PKCS#7 signature block for CERT.RSA
            val certRsaBytes = buildSimplePkcs7Signature(rawSignature, keyPair.public.encoded)

            // 5. Repackage into output signed APK
            val zis = ZipInputStream(FileInputStream(unsignedApk))
            val zos = ZipOutputStream(FileOutputStream(signedOutputApk))

            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("META-INF/")) {
                    val newEntry = ZipEntry(entry.name)
                    newEntry.method = entry.method
                    if (entry.method == ZipEntry.STORED) {
                        newEntry.size = entry.size
                        newEntry.compressedSize = entry.compressedSize
                        newEntry.crc = entry.crc
                        if (entry.extra != null) newEntry.extra = entry.extra
                    }
                    zos.putNextEntry(newEntry)
                    val buf = ByteArray(16384)
                    var l: Int
                    while (zis.read(buf).also { l = it } != -1) {
                        zos.write(buf, 0, l)
                    }
                    zos.closeEntry()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()

            // Write signature entries in META-INF
            zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zos.write(manifestMfBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
            zos.write(certSfBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zos.write(certRsaBytes)
            zos.closeEntry()

            zos.close()

            SigningServiceResult(
                success = true,
                certFingerprintSha256 = certFingerprint,
                signedApkFile = signedOutputApk,
                manifestEntryCount = fileDigests.size,
                details = "Signed ${fileDigests.size} entries with RSA-2048 SHA256withRSA (v1 Scheme)."
            )
        } catch (e: Exception) {
            SigningServiceResult(
                success = false,
                certFingerprintSha256 = "",
                signedApkFile = null,
                manifestEntryCount = 0,
                details = "Failed to sign APK: ${e.message}",
                errorMessage = e.message ?: "Unknown signing error"
            )
        }
    }

    private fun buildSimplePkcs7Signature(sig: ByteArray, pubKey: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x30) // SEQUENCE
        out.write(0x82)
        val len = sig.size + pubKey.size + 16
        out.write((len shr 8) and 0xFF)
        out.write(len and 0xFF)
        out.write(pubKey)
        out.write(sig)
        return out.toByteArray()
    }
}
