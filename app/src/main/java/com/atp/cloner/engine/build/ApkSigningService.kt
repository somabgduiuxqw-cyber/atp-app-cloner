package com.atp.cloner.engine.build

import android.content.Context
import com.atp.cloner.engine.ApkSignerEngine
import java.io.File

class ApkSigningService(private val context: Context) {

    private val signerEngine = ApkSignerEngine(context)

    data class SigningOutput(
        val success: Boolean,
        val signedApkFile: File?,
        val certFingerprint: String,
        val algorithm: String = "SHA256withRSA",
        val errorMessage: String? = null
    )

    fun signApk(unsignedApkFile: File, outputSignedFile: File): SigningOutput {
        return try {
            val result = signerEngine.signApk(unsignedApkFile, outputSignedFile)
            if (result.success && outputSignedFile.exists() && outputSignedFile.length() > 0) {
                SigningOutput(
                    success = true,
                    signedApkFile = outputSignedFile,
                    certFingerprint = result.certFingerprintSha256
                )
            } else {
                SigningOutput(
                    success = false,
                    signedApkFile = null,
                    certFingerprint = "",
                    errorMessage = result.errorMessage ?: "Failed to sign APK"
                )
            }
        } catch (e: Exception) {
            SigningOutput(
                success = false,
                signedApkFile = null,
                certFingerprint = "",
                errorMessage = e.message
            )
        }
    }
}
