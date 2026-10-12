package com.atp.cloner.engine.build

import com.atp.cloner.engine.ZipAligner
import java.io.File

class ApkAlignmentService {

    private val zipAligner = ZipAligner()

    data class AlignmentResult(
        val success: Boolean,
        val alignedFile: File,
        val alignedEntriesCount: Int,
        val errorMessage: String? = null
    )

    fun alignApk(unalignedFile: File, outputFile: File): AlignmentResult {
        return try {
            val success = zipAligner.alignApk(unalignedFile, outputFile)
            if (success && outputFile.exists()) {
                // Count entries in aligned file
                val zip = java.util.zip.ZipFile(outputFile)
                val entriesCount = zip.size()
                zip.close()
                AlignmentResult(true, outputFile, entriesCount)
            } else {
                AlignmentResult(false, outputFile, 0, "Zip alignment execution failed")
            }
        } catch (e: Exception) {
            AlignmentResult(false, outputFile, 0, e.message)
        }
    }
}
