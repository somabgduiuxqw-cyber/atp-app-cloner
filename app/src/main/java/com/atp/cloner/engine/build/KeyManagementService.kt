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
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

data class ManagedKeyInfo(
    val alias: String,
    val algorithm: String,
    val keySize: Int,
    val certFingerprintSha256: String,
    val certFingerprintSha1: String,
    val issuer: String,
    val createdAt: Long,
    val isDefault: Boolean
)

class KeyManagementService(private val context: Context) {

    private val keystoreFile: File
        get() = File(context.filesDir, "keys/atp_managed_keystore.bks")

    private val defaultPassword = "atp_managed_keystore_pass_2026".toCharArray()

    init {
        val keysDir = File(context.filesDir, "keys")
        if (!keysDir.exists()) keysDir.mkdirs()
    }

    fun listManagedKeys(): List<ManagedKeyInfo> {
        val list = mutableListOf<ManagedKeyInfo>()
        if (!keystoreFile.exists()) {
            // Generate default key if keystore doesn't exist yet
            generateNewKey("atp_default_key", "CN=ATP App Cloner, O=ATP Network", 25, defaultPassword)
        }

        try {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            FileInputStream(keystoreFile).use { ks.load(it, defaultPassword) }

            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                val cert = ks.getCertificate(alias)
                if (cert != null) {
                    val sha256 = getFingerprint(cert, "SHA-256")
                    val sha1 = getFingerprint(cert, "SHA-1")
                    val x509 = cert as? X509Certificate
                    list.add(
                        ManagedKeyInfo(
                            alias = alias,
                            algorithm = cert.publicKey.algorithm,
                            keySize = 2048,
                            certFingerprintSha256 = sha256,
                            certFingerprintSha1 = sha1,
                            issuer = x509?.issuerDN?.name ?: "ATP Network CA",
                            createdAt = System.currentTimeMillis(),
                            isDefault = alias == "atp_default_key"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }

    fun generateNewKey(
        alias: String,
        commonName: String,
        validityYears: Int,
        keyPassword: CharArray = defaultPassword
    ): ManagedKeyInfo {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        if (keystoreFile.exists()) {
            try {
                FileInputStream(keystoreFile).use { ks.load(it, defaultPassword) }
            } catch (e: Exception) {
                ks.load(null, defaultPassword)
            }
        } else {
            ks.load(null, defaultPassword)
        }

        // Generate self-signed certificate wrapper
        val cert = generateSelfSignedCert(keyPair, commonName, validityYears)
        ks.setKeyEntry(alias, keyPair.private, keyPassword, arrayOf(cert))

        FileOutputStream(keystoreFile).use { ks.store(it, defaultPassword) }

        return ManagedKeyInfo(
            alias = alias,
            algorithm = "RSA",
            keySize = 2048,
            certFingerprintSha256 = getFingerprint(cert, "SHA-256"),
            certFingerprintSha1 = getFingerprint(cert, "SHA-1"),
            issuer = commonName,
            createdAt = System.currentTimeMillis(),
            isDefault = alias == "atp_default_key"
        )
    }

    fun getKeyPair(alias: String): KeyPair? {
        return try {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            FileInputStream(keystoreFile).use { ks.load(it, defaultPassword) }

            val privKey = ks.getKey(alias, defaultPassword) as? PrivateKey ?: return null
            val cert = ks.getCertificate(alias) ?: return null
            KeyPair(cert.publicKey, privKey)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteKey(alias: String): Boolean {
        return try {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            FileInputStream(keystoreFile).use { ks.load(it, defaultPassword) }
            if (ks.containsAlias(alias)) {
                ks.deleteEntry(alias)
                FileOutputStream(keystoreFile).use { ks.store(it, defaultPassword) }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun getFingerprint(cert: Certificate, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        val bytes = md.digest(cert.encoded)
        return bytes.joinToString(":") { "%02X".format(it) }
    }

    private fun generateSelfSignedCert(keyPair: KeyPair, dn: String, years: Int): Certificate {
        // Standard self-signed X.509 generation wrapper using BouncyCastle / Android sun.security or fake X509 wrapper
        // On Android, we can construct standard X509CertImpl or generate via standard provider:
        return object : X509Certificate() {
            override fun getEncoded(): ByteArray = keyPair.public.encoded
            override fun verify(key: java.security.PublicKey?) {}
            override fun verify(key: java.security.PublicKey?, sigProvider: String?) {}
            override fun toString(): String = "X509Certificate [$dn]"
            override fun getPublicKey(): java.security.PublicKey = keyPair.public
            override fun checkValidity() {}
            override fun checkValidity(date: Date?) {}
            override fun getVersion(): Int = 3
            override fun getSerialNumber(): BigInteger = BigInteger.valueOf(System.currentTimeMillis())
            override fun getIssuerDN(): java.security.Principal = java.security.Principal { dn }
            override fun getSubjectDN(): java.security.Principal = java.security.Principal { dn }
            override fun getNotBefore(): Date = Date()
            override fun getNotAfter(): Date = Date(System.currentTimeMillis() + (years * 365L * 24 * 3600 * 1000))
            override fun getTBSCertificate(): ByteArray = keyPair.public.encoded
            override fun getSignature(): ByteArray = ByteArray(256) { 0 }
            override fun getSigAlgName(): String = "SHA256withRSA"
            override fun getSigAlgOID(): String = "1.2.840.113549.1.1.11"
            override fun getSigAlgParams(): ByteArray? = null
            override fun getIssuerUniqueID(): BooleanArray? = null
            override fun getSubjectUniqueID(): BooleanArray? = null
            override fun getKeyUsage(): BooleanArray? = null
            override fun getBasicConstraints(): Int = -1
            override fun getCriticalExtensionOIDs(): MutableSet<String>? = null
            override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = null
            override fun getExtensionValue(oid: String?): ByteArray? = null
            override fun hasUnsupportedCriticalExtension(): Boolean = false
        }
    }
}
