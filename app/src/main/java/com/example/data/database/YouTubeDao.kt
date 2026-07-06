package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubeDao {
    @Query("SELECT * FROM youtube_channels LIMIT 1")
    fun getChannelFlow(): Flow<YouTubeChannelEntity?>

    @Query("SELECT * FROM youtube_channels LIMIT 1")
    suspend fun getChannelDirect(): YouTubeChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: YouTubeChannelEntity)

    @Query("DELETE FROM youtube_channels")
    suspend fun clearChannel()

    @Query("SELECT * FROM youtube_videos ORDER BY publishTime DESC")
    fun getAllVideosFlow(): Flow<List<YouTubeVideoEntity>>

    @Query("SELECT * FROM youtube_videos ORDER BY publishTime DESC")
    suspend fun getAllVideosDirect(): List<YouTubeVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<YouTubeVideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: YouTubeVideoEntity)

    @Query("DELETE FROM youtube_videos")
    suspend fun clearVideos()

    @Query("SELECT * FROM youtube_analytics_snapshots ORDER BY timestamp ASC")
    fun getAnalyticsSnapshotsFlow(): Flow<List<YouTubeAnalyticsSnapshot>>

    @Query("SELECT * FROM youtube_analytics_snapshots ORDER BY timestamp ASC")
    suspend fun getAnalyticsSnapshotsDirect(): List<YouTubeAnalyticsSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalyticsSnapshot(snapshot: YouTubeAnalyticsSnapshot)

    @Query("DELETE FROM youtube_analytics_snapshots")
    suspend fun clearAnalyticsSnapshots()

    @Query("SELECT * FROM youtube_upload_drafts ORDER BY lastUpdated DESC")
    fun getUploadDraftsFlow(): Flow<List<YouTubeUploadDraft>>

    @Query("SELECT * FROM youtube_upload_drafts ORDER BY lastUpdated DESC")
    suspend fun getUploadDraftsDirect(): List<YouTubeUploadDraft>

    @Query("SELECT * FROM youtube_upload_drafts WHERE id = :id LIMIT 1")
    suspend fun getUploadDraftById(id: Int): YouTubeUploadDraft?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadDraft(draft: YouTubeUploadDraft): Long

    @Delete
    suspend fun deleteUploadDraft(draft: YouTubeUploadDraft)

    @Query("DELETE FROM youtube_upload_drafts")
    suspend fun clearUploadDrafts()
}
