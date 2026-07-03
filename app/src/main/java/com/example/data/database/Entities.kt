package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portfolio_assets")
data class PortfolioAsset(
    @PrimaryKey val symbol: String,
    val displayName: String,
    val amountOwned: Double,
    val averageBuyPrice: Double,
    val assetType: String, // CRYPTO, STOCK
    val currentPrice: Double
)

@Entity(tableName = "content_items")
data class ContentItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val platform: String, // youtube, instagram, tiktok
    val scriptOutline: String,
    val status: String, // DRAFT, SCHEDULED, PUBLISHED
    val engagementCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "freelance_gigs")
data class FreelanceGig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val budget: Double,
    val clientName: String,
    val status: String, // AVAILABLE, APPLIED, COMPLETED, PAID
    val proposalText: String? = null
)

@Entity(tableName = "transaction_records")
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String // DEPOSIT, WITHDRAWAL, BUY, SELL, FREELANCE
)

@Entity(tableName = "account_linking")
data class AccountLinking(
    @PrimaryKey val id: Int = 1,
    val totalBalance: Double = 5000.0, // Initial dummy balance
    val isTradingLinked: Boolean = false,
    val isSocialLinked: Boolean = false,
    val isFreelanceLinked: Boolean = false
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // USER, AZA, SYSTEM
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "aza_channels")
data class AzaChannel(
    @PrimaryKey val platform: String, // YOUTUBE, INSTAGRAM, TIKTOK
    val handle: String,
    val followersCount: Int = 120,
    val totalVideosPublished: Int = 0,
    val totalLikesAccumulated: Int = 0,
    val targetSegment: String = "Creators & Devs",
    val autonomousInsight: String = "No insights collected yet. Toggle Autopilot to begin automatic optimization."
)

@Entity(tableName = "execution_records")
data class ExecutionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val objectiveId: Int,
    val taskId: Int = 0,
    val agent: String,
    val skill: String,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMs: Long,
    val retries: Int,
    val success: Boolean,
    val error: String?,
    val outputSummary: String,
    val input: String = "",
    val output: String = ""
)

@Entity(tableName = "aza_learning_records")
data class AzaLearningRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val skillName: String,
    val success: Boolean,
    val failure: Boolean,
    val latencyMs: Long,
    val confidence: Float,
    val retryCount: Int,
    val userApproval: Boolean,
    val outcomeQuality: Double = 1.0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "binance_orders")
data class BinanceOrderEntity(
    @PrimaryKey val orderId: String,
    val symbol: String,
    val side: String,
    val type: String,
    val price: Double,
    val origQty: Double,
    val executedQty: Double,
    val status: String,
    val timestamp: Long
)

@Entity(tableName = "binance_trades")
data class BinanceTradeEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val symbol: String,
    val price: Double,
    val qty: Double,
    val commission: Double,
    val commissionAsset: String,
    val timestamp: Long,
    val isBuyer: Boolean
)



