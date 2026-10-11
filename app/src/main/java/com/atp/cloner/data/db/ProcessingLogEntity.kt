package com.atp.cloner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processing_logs")
data class ProcessingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val stage: String,
    val message: String,
    val status: String = "INFO" // INFO, SUCCESS, WARNING, ERROR
)
