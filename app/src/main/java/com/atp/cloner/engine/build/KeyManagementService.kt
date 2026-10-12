package com.atp.cloner.engine.build

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate

class KeyManagementService(private val context: Context) {

    private val keyAlias = "atp_cloner_key"
    private val keyPassword = "atp_network_secure_pass".toCharArray()

    data class KeyConfig(
        val keyPair: KeyPair,
        val certificate: Certificate?,
        val certFingerprintSha256: String
    )

    fun getOrCreateSigningKey(): KeyConfig {
        val keystoreFile = File(context.filesDir, "atp_signing_keystore.bks")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())

        if (keystoreFile.exists()) {
            try {
                FileInputStream(keystoreFile).use { input ->
                    keyStore.load(input, keyPassword)
                    val privKey = keyStore.getKey(keyAlias, keyPassword) as? PrivateKey
                    val cert = keyStore.getCertificate(keyAlias)
                    if (privKey != null && cert != null) {
                        val fingerprint = calculateCertFingerprint(cert)
                        return KeyConfig(KeyPair(cert.publicKey, privKey), cert, fingerprint)
                    }
                }
            } catch (e: Exception) {
                // Keystore format or corruption, generate new
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        val digest = MessageDigest.getInstance("SHA-256")
        val fingerprint = digest.digest(keyPair.public.encoded).joinToString(":") { "%02X".format(it) }

        return KeyConfig(keyPair, null, fingerprint)
    }

    private fun calculateCertFingerprint(cert: Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(cert.encoded).joinToString(":") { "%02X".format(it) }
    }
}
