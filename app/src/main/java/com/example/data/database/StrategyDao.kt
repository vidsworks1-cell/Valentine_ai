package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StrategyDao {
    @Query("SELECT * FROM strategy_metadata ORDER BY ranking ASC, strategyName ASC")
    fun getAllStrategiesFlow(): Flow<List<StrategyEntity>>

    @Query("SELECT * FROM strategy_metadata ORDER BY ranking ASC, strategyName ASC")
    suspend fun getAllStrategiesDirect(): List<StrategyEntity>

    @Query("SELECT * FROM strategy_metadata WHERE strategyName = :name LIMIT 1")
    suspend fun getStrategyByNameDirect(name: String): StrategyEntity?

    @Query("SELECT * FROM strategy_metadata WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveStrategyDirect(): StrategyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrategies(strategies: List<StrategyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrategy(strategy: StrategyEntity)

    @Update
    suspend fun updateStrategy(strategy: StrategyEntity)

    @Transaction
    suspend fun setActiveStrategy(strategyName: String) {
        clearActiveStrategy()
        markStrategyActive(strategyName)
    }

    @Query("UPDATE strategy_metadata SET isActive = 0")
    suspend fun clearActiveStrategy()

    @Query("UPDATE strategy_metadata SET isActive = 1 WHERE strategyName = :strategyName")
    suspend fun markStrategyActive(strategyName: String)

    @Query("SELECT * FROM strategy_backtest_reports ORDER BY timestamp DESC")
    fun getAllBacktestReportsFlow(): Flow<List<StrategyBacktestReport>>

    @Query("SELECT * FROM strategy_backtest_reports WHERE strategyName = :strategyName ORDER BY timestamp DESC")
    fun getBacktestReportsForStrategy(strategyName: String): Flow<List<StrategyBacktestReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBacktestReport(report: StrategyBacktestReport): Long

    @Query("SELECT * FROM strategy_optimization_history ORDER BY timestamp DESC")
    fun getAllOptimizationRecordsFlow(): Flow<List<StrategyOptimizationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOptimizationRecord(record: StrategyOptimizationRecord)

    @Query("DELETE FROM strategy_optimization_history")
    suspend fun clearAllOptimizationRecords()

    @Query("DELETE FROM strategy_metadata")
    suspend fun clearAllStrategies()

    @Query("DELETE FROM strategy_backtest_reports")
    suspend fun clearAllBacktests()

    // Trade Audit Logs
    @Query("SELECT * FROM trade_audit_logs ORDER BY timestamp DESC")
    fun getAllTradeAuditLogsFlow(): Flow<List<TradeAuditLog>>

    @Query("SELECT * FROM trade_audit_logs ORDER BY timestamp DESC")
    suspend fun getAllTradeAuditLogsDirect(): List<TradeAuditLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTradeAuditLog(log: TradeAuditLog)

    @Query("DELETE FROM trade_audit_logs")
    suspend fun clearAllTradeAuditLogs()

    // Exported Report History
    @Query("SELECT * FROM exported_report_history ORDER BY timestamp DESC")
    fun getAllExportedReportsFlow(): Flow<List<ExportedReportHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportedReport(report: ExportedReportHistory)

    @Query("DELETE FROM exported_report_history WHERE id = :id")
    suspend fun deleteExportedReportById(id: Int)

    @Query("DELETE FROM exported_report_history")
    suspend fun clearAllExportedReports()
}
