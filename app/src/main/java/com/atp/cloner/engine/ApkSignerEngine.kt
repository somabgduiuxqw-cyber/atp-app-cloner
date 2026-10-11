package com.atp.cloner.engine

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
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ApkSignerEngine(private val context: Context) {

    private val keyAlias = "atp_network_cloner_key"
    private val keyPassword = "atp_cloner_local_pass".toCharArray()

    data class SigningResult(
        val success: Boolean,
        val certFingerprintSha256: String,
        val signedFile: File?,
        val errorMessage: String? = null
    )

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
                // Regenerate if corrupt
            }
        }

        // Generate RSA-2048 key pair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        return keyPair
    }

    fun getCertificateFingerprint(keyPair: KeyPair): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(keyPair.public.encoded)
        return hash.joinToString(":") { "%02X".format(it) }
    }

    fun signApk(unsignedApk: File, signedOutputApk: File): SigningResult {
        return try {
            val keyPair = getOrGenerateKeyPair()
            val certFingerprint = getCertificateFingerprint(keyPair)

            val fileDigests = mutableMapOf<String, String>()
            val zipFile = ZipFile(unsignedApk)

            // 1. Calculate SHA-256 of each entry (excluding META-INF)
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
            zipFile.close()

            // 2. Build MANIFEST.MF
            val manifestMfSb = StringBuilder()
            manifestMfSb.append("Manifest-Version: 1.0\r\n")
            manifestMfSb.append("Created-By: 1.0 (ATP App Cloner - ATP Network)\r\n\r\n")
            for ((name, hash) in fileDigests) {
                manifestMfSb.append("Name: $name\r\n")
                manifestMfSb.append("SHA-256-Digest: $hash\r\n\r\n")
            }
            val manifestMfBytes = manifestMfSb.toString().toByteArray(Charsets.UTF_8)

            // 3. Build CERT.SF
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

            // 4. Sign CERT.SF using RSA SHA256withRSA
            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(keyPair.private)
            signer.update(certSfBytes)
            val rawSignature = signer.sign()

            // Construct PKCS7 wrapper block
            val certRsaBytes = buildSimplePkcs7Signature(rawSignature, keyPair.public.encoded)

            // 5. Write output signed ZIP
            val zis = ZipInputStream(FileInputStream(unsignedApk))
            val zos = ZipOutputStream(FileOutputStream(signedOutputApk))

            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("META-INF/")) {
                    zos.putNextEntry(ZipEntry(entry.name))
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

            // Add signature files
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

            SigningResult(
                success = true,
                certFingerprintSha256 = certFingerprint,
                signedFile = signedOutputApk
            )
        } catch (e: Exception) {
            SigningResult(
                success = false,
                certFingerprintSha256 = "",
                signedFile = null,
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
