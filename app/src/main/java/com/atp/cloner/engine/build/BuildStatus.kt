package com.atp.cloner.engine.build

sealed class BuildStatus {
    data class Progress(
        val stageName: String,
        val stepNumber: Int,
        val totalSteps: Int = 8,
        val progressPercent: Int,
        val details: String
    ) : BuildStatus()

    data class Success(val report: ApkBuildReport) : BuildStatus()
    data class Failed(val error: String, val technicalDetail: String? = null) : BuildStatus()
}
