package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "laptop_documents")
data class LaptopDocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val category: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val retrievedAt: Long
)

@Entity(tableName = "laptop_exchange_history")
data class LaptopExchangeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceName: String,
    val direction: String, // "PUSH" or "PULL"
    val dataCount: Int,
    val timestamp: Long,
    val status: String, // "SUCCESS" or "FAILED"
    val message: String
)

@Dao
interface LaptopDao {
    @Query("SELECT * FROM laptop_documents ORDER BY lastModified DESC")
    fun getAllDocumentsFlow(): Flow<List<LaptopDocumentEntity>>

    @Query("SELECT * FROM laptop_documents ORDER BY lastModified DESC")
    suspend fun getAllDocumentsDirect(): List<LaptopDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(documents: List<LaptopDocumentEntity>)

    @Query("DELETE FROM laptop_documents")
    suspend fun clearAllDocuments()

    @Query("SELECT * FROM laptop_exchange_history ORDER BY timestamp DESC")
    fun getAllExchangeRecordsFlow(): Flow<List<LaptopExchangeRecord>>

    @Query("SELECT * FROM laptop_exchange_history ORDER BY timestamp DESC")
    suspend fun getAllExchangeRecordsDirect(): List<LaptopExchangeRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRecord(record: LaptopExchangeRecord): Long

    @Query("DELETE FROM laptop_exchange_history")
    suspend fun clearAllExchangeHistory()
}
