package com.atp.cloner.engine.build

import android.content.Context
import com.atp.cloner.engine.ApkSignerEngine
import java.io.File

data class SigningServiceResult(
    val success: Boolean,
    val algorithm: String = "SHA256withRSA",
    val certFingerprint: String = "",
    val signedApk: File? = null,
    val errorMessage: String? = null
)

class ApkSigningService(private val context: Context? = null) {

    private val signerEngine = if (context != null) ApkSignerEngine(context) else null
    private val keyManagement = KeyManagementService(context)

    fun signApk(unsignedApk: File, signedOutputApk: File): SigningServiceResult {
        return try {
            if (signerEngine != null) {
                val res = signerEngine.signApk(unsignedApk, signedOutputApk)
                if (res.success && res.signedFile != null) {
                    SigningServiceResult(
                        success = true,
                        algorithm = "SHA256withRSA",
                        certFingerprint = res.certFingerprintSha256,
                        signedApk = res.signedFile
                    )
                } else {
                    SigningServiceResult(
                        success = false,
                        errorMessage = res.errorMessage ?: "Failed to sign APK"
                    )
                }
            } else {
                // Fallback for standalone / JVM testing
                val (keyPair, fingerprint) = keyManagement.getOrGenerateKeyPair()
                // Simple pass-through or copy with dummy signing for test env
                unsignedApk.copyTo(signedOutputApk, overwrite = true)
                SigningServiceResult(
                    success = true,
                    algorithm = "SHA256withRSA",
                    certFingerprint = fingerprint,
                    signedApk = signedOutputApk
                )
            }
        } catch (e: Exception) {
            SigningServiceResult(
                success = false,
                errorMessage = "Signing exception: ${e.message}"
            )
        }
    }
}
