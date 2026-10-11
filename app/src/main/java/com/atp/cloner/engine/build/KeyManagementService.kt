package com.atp.cloner.engine.build

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Date

data class SigningKeyInfo(
    val alias: String,
    val algorithm: String,
    val keySize: Int,
    val sha256Fingerprint: String
)

class KeyManagementService(private val context: Context? = null) {

    private val defaultAlias = "atp_cloner_release_key"
    private val defaultPassword = "atp_network_keystore_pass".toCharArray()

    fun getOrGenerateKeyPair(): Pair<KeyPair, String> {
        val keystoreFile = if (context != null) {
            File(context.filesDir, "atp_release_keystore.bks")
        } else {
            File(System.getProperty("java.io.tmpdir"), "atp_release_keystore.bks")
        }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())

        if (keystoreFile.exists()) {
            try {
                FileInputStream(keystoreFile).use { input ->
                    keyStore.load(input, defaultPassword)
                    val privKey = keyStore.getKey(defaultAlias, defaultPassword) as? PrivateKey
                    val cert = keyStore.getCertificate(defaultAlias)
                    if (privKey != null && cert != null) {
                        val fingerprint = computeFingerprint(cert.publicKey)
                        return Pair(KeyPair(cert.publicKey, privKey), fingerprint)
                    }
                }
            } catch (e: Exception) {
                // Regenerate if error
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()
        val fingerprint = computeFingerprint(keyPair.public)

        return Pair(keyPair, fingerprint)
    }

    fun computeFingerprint(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey.encoded)
        return hash.joinToString(":") { "%02X".format(it) }
    }

    fun getKeyInfo(): SigningKeyInfo {
        val (keyPair, fingerprint) = getOrGenerateKeyPair()
        return SigningKeyInfo(
            alias = defaultAlias,
            algorithm = "RSA",
            keySize = 2048,
            sha256Fingerprint = fingerprint
        )
    }
}
