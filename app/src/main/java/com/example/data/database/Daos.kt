package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolio_assets")
    fun getAllAssets(): Flow<List<PortfolioAsset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: PortfolioAsset)

    @Update
    suspend fun updateAsset(asset: PortfolioAsset)

    @Delete
    suspend fun deleteAsset(asset: PortfolioAsset)

    @Query("SELECT * FROM portfolio_assets WHERE symbol = :symbol LIMIT 1")
    suspend fun getAssetBySymbol(symbol: String): PortfolioAsset?
}

@Dao
interface ContentDao {
    @Query("SELECT * FROM content_items ORDER BY createdAt DESC")
    fun getAllContent(): Flow<List<ContentItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(contentItem: ContentItem)

    @Update
    suspend fun updateContent(contentItem: ContentItem)

    @Query("DELETE FROM content_items WHERE id = :id")
    suspend fun deleteContentById(id: Int)
}

@Dao
interface GigDao {
    @Query("SELECT * FROM freelance_gigs")
    fun getAllGigs(): Flow<List<FreelanceGig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGig(gig: FreelanceGig)

    @Update
    suspend fun updateGig(gig: FreelanceGig)

    @Query("SELECT * FROM freelance_gigs WHERE id = :id LIMIT 1")
    suspend fun getGigById(id: Int): FreelanceGig?
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transaction_records ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(record: TransactionRecord)
}

@Dao
interface AccountLinkingDao {
    @Query("SELECT * FROM account_linking WHERE id = 1 LIMIT 1")
    fun getAccountInfoFlow(): Flow<AccountLinking?>

    @Query("SELECT * FROM account_linking WHERE id = 1 LIMIT 1")
    suspend fun getAccountInfoDirect(): AccountLinking?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountInfo(info: AccountLinking)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesDirect(): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM aza_channels")
    fun getAllChannelsFlow(): Flow<List<AzaChannel>>

    @Query("SELECT * FROM aza_channels")
    suspend fun getAllChannelsDirect(): List<AzaChannel>

    @Query("SELECT * FROM aza_channels WHERE platform = :platform LIMIT 1")
    suspend fun getChannelByPlatform(platform: String): AzaChannel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: AzaChannel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<AzaChannel>)
}

@Dao
interface ExecutionRecordDao {
    @Query("SELECT * FROM execution_records ORDER BY startedAt DESC")
    fun getAllExecutionRecordsFlow(): Flow<List<ExecutionRecord>>

    @Query("SELECT * FROM execution_records WHERE objectiveId = :objectiveId ORDER BY startedAt DESC")
    suspend fun getExecutionRecordsDirect(objectiveId: Int): List<ExecutionRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecutionRecord(record: ExecutionRecord): Long

    @Query("DELETE FROM execution_records")
    suspend fun clearAllExecutionRecords()
}

@Dao
interface AzaLearningRecordDao {
    @Query("SELECT * FROM aza_learning_records ORDER BY timestamp DESC")
    fun getAllLearningRecordsFlow(): Flow<List<AzaLearningRecord>>

    @Query("SELECT * FROM aza_learning_records WHERE skillName = :skillName ORDER BY timestamp DESC")
    suspend fun getRecordsForSkillDirect(skillName: String): List<AzaLearningRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearningRecord(record: AzaLearningRecord): Long

    @Query("DELETE FROM aza_learning_records")
    suspend fun clearAllLearningRecords()
}

@Dao
interface BinanceDao {
    @Query("SELECT * FROM binance_orders ORDER BY timestamp DESC")
    fun getAllBinanceOrdersFlow(): Flow<List<BinanceOrderEntity>>

    @Query("SELECT * FROM binance_orders ORDER BY timestamp DESC")
    suspend fun getAllBinanceOrdersDirect(): List<BinanceOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBinanceOrders(orders: List<BinanceOrderEntity>)

    @Query("DELETE FROM binance_orders")
    suspend fun clearAllBinanceOrders()

    @Query("SELECT * FROM binance_trades ORDER BY timestamp DESC")
    fun getAllBinanceTradesFlow(): Flow<List<BinanceTradeEntity>>

    @Query("SELECT * FROM binance_trades ORDER BY timestamp DESC")
    suspend fun getAllBinanceTradesDirect(): List<BinanceTradeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBinanceTrades(trades: List<BinanceTradeEntity>)

    @Query("DELETE FROM binance_trades")
    suspend fun clearAllBinanceTrades()
}



