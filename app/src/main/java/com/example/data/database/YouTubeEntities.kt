package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "youtube_channels")
data class YouTubeChannelEntity(
    @PrimaryKey val channelId: String,
    val channelName: String,
    val profilePictureUrl: String,
    val handle: String,
    val subscriberCount: Long,
    val totalVideos: Long,
    val totalViews: Long,
    val channelCreationDate: String,
    val apiQuotaUsage: Int = 0,
    val apiQuotaLimit: Int = 10000,
    val lastSyncTime: Long = 0L,
    val tokenExpirationTime: Long = 0L,
    val currentOAuthScopes: String = ""
)

@Entity(tableName = "youtube_videos")
data class YouTubeVideoEntity(
    @PrimaryKey val videoId: String,
    val channelId: String,
    val title: String,
    val description: String,
    val tags: String, // Comma-separated
    val privacyStatus: String, // PRIVATE, UNLISTED, PUBLIC
    val scheduledPublishTime: Long? = null,
    val thumbnailUrl: String,
    val isShort: Boolean = false,
    val views: Long = 0,
    val likes: Long = 0,
    val commentsCount: Long = 0,
    val watchTimeHrs: Double = 0.0,
    val averageViewDurationSecs: Long = 0,
    val ctrPercent: Double = 0.0,
    val publishTime: Long = 0L
)

@Entity(tableName = "youtube_analytics_snapshots")
data class YouTubeAnalyticsSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val views: Long,
    val watchTimeHrs: Double,
    val subscribers: Long,
    val averageViewDurationSecs: Long,
    val likes: Long,
    val comments: Long,
    val growthRatePercent: Double,
    val ctrPercent: Double
)

@Entity(tableName = "youtube_upload_drafts")
data class YouTubeUploadDraft(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val tags: String,
    val categoryId: String = "22", // People & Blogs
    val privacyStatus: String = "PRIVATE", // PRIVATE, UNLISTED, PUBLIC
    val scheduledPublishTime: Long? = null,
    val localVideoUri: String = "",
    val thumbnailUri: String = "",
    val audienceSelection: String = "not_for_kids", // not_for_kids, made_for_kids
    val language: String = "en",
    val estimatedDurationSecs: Long = 0,
    val warnings: String = "None",
    val approvalStatus: String = "DRAFT", // DRAFT, PENDING_APPROVAL, APPROVED, UPLOADING, PUBLISHED, ERROR
    val isApproved: Boolean = false,
    val uploadProgress: Int = 0,
    val processingStatus: String = "NONE", // NONE, PROCESSING, COMPLETED, FAILED
    val errorMessage: String? = null,
    val youtubeVideoId: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
