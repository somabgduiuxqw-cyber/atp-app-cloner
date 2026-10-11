package com.atp.cloner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessingLogDao {
    @Query("SELECT * FROM processing_logs WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun getLogsForProjectFlow(projectId: String): Flow<List<ProcessingLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ProcessingLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<ProcessingLogEntity>)

    @Query("DELETE FROM processing_logs WHERE projectId = :projectId")
    suspend fun deleteLogsForProject(projectId: String)
}
