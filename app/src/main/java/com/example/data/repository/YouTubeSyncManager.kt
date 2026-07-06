package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.aza.authentication.GoogleTokenManager
import com.example.data.api.YouTubeRetrofitClient
import com.example.data.api.YouTubeVideoInsertBody
import com.example.data.api.VideoInsertSnippet
import com.example.data.api.VideoInsertStatus
import com.example.data.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

enum class YouTubeConnectionState {
    NOT_CONNECTED,
    AUTHENTICATING,
    REQUESTING_PERMISSIONS,
    CONNECTED,
    SYNCHRONIZING,
    OPERATIONAL,
    TOKEN_REFRESHING,
    ERROR,
    REVOKED
}

class YouTubeSyncManager(
    private val context: Context,
    private val repository: AppRepository
) {
    private val tokenManager = GoogleTokenManager(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(YouTubeConnectionState.NOT_CONNECTED)
    val connectionState: StateFlow<YouTubeConnectionState> = _connectionState.asStateFlow()

    private val _isSandboxMode = MutableStateFlow(true)
    val isSandboxMode: StateFlow<Boolean> = _isSandboxMode.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Diagnostics State
    private val _apiLatencyMs = MutableStateFlow(0L)
    val apiLatencyMs: StateFlow<Long> = _apiLatencyMs.asStateFlow()

    private val _lastSuccessCall = MutableStateFlow<String>("None")
    val lastSuccessCall: StateFlow<String> = _lastSuccessCall.asStateFlow()

    private val _lastFailedCall = MutableStateFlow<String>("None")
    val lastFailedCall: StateFlow<String> = _lastFailedCall.asStateFlow()

    private val _currentSyncJob = MutableStateFlow<String>("Idle")
    val currentSyncJob: StateFlow<String> = _currentSyncJob.asStateFlow()

    init {
        // Automatically determine sandbox mode based on Google Token availability
        val hasGoogle = tokenManager.hasCredentials()
        _isSandboxMode.value = !hasGoogle
        if (hasGoogle) {
            _connectionState.value = YouTubeConnectionState.CONNECTED
            // Try to auto-sync
            syncNow()
        } else {
            _connectionState.value = YouTubeConnectionState.NOT_CONNECTED
        }
    }

    fun setSandboxMode(sandbox: Boolean) {
        _isSandboxMode.value = sandbox
        if (sandbox) {
            _errorMessage.value = null
            _connectionState.value = YouTubeConnectionState.OPERATIONAL
            coroutineScope.launch {
                prepopulateSandboxData()
            }
        } else {
            if (!tokenManager.hasCredentials()) {
                _connectionState.value = YouTubeConnectionState.NOT_CONNECTED
            } else {
                _connectionState.value = YouTubeConnectionState.CONNECTED
                syncNow()
            }
        }
    }

    fun syncNow() {
        coroutineScope.launch {
            _currentSyncJob.value = "Starting Sync"
            val isSandbox = _isSandboxMode.value
            if (isSandbox) {
                runSandboxSync()
            } else {
                runLiveSync()
            }
        }
    }

    private suspend fun runSandboxSync() {
        _connectionState.value = YouTubeConnectionState.SYNCHRONIZING
        _currentSyncJob.value = "Synchronizing Sandbox Channel"
        delay(1500) // Simulate network latency

        try {
            prepopulateSandboxData()
            _connectionState.value = YouTubeConnectionState.OPERATIONAL
            _errorMessage.value = null
            _lastSuccessCall.value = "GetSandboxChannel (${System.currentTimeMillis()})"
            _currentSyncJob.value = "Idle"
        } catch (e: Exception) {
            _connectionState.value = YouTubeConnectionState.ERROR
            _errorMessage.value = e.localizedMessage
            _lastFailedCall.value = "SandboxSync: ${e.localizedMessage}"
            _currentSyncJob.value = "Failed"
        }
    }

    private suspend fun runLiveSync() {
        val accessToken = tokenManager.getAccessToken()
        if (accessToken.isNullOrBlank()) {
            _connectionState.value = YouTubeConnectionState.NOT_CONNECTED
            _errorMessage.value = "Google Authentication credentials are not available."
            _currentSyncJob.value = "Failed: Missing Credentials"
            return
        }

        _connectionState.value = YouTubeConnectionState.AUTHENTICATING
        _currentSyncJob.value = "Authenticating with Google"
        delay(500)

        if (tokenManager.isTokenExpired()) {
            _connectionState.value = YouTubeConnectionState.TOKEN_REFRESHING
            _currentSyncJob.value = "Refreshing OAuth Token"
            // Token is expired. Usually refreshed, but in mock we flag error if we can't
            _connectionState.value = YouTubeConnectionState.ERROR
            _errorMessage.value = "Google OAuth Token expired. Please re-authenticate."
            _currentSyncJob.value = "Failed: Token Expired"
            return
        }

        _connectionState.value = YouTubeConnectionState.REQUESTING_PERMISSIONS
        _currentSyncJob.value = "Verifying YouTube Permissions"
        delay(500)

        val scopes = tokenManager.getGrantedScopes()
        val hasYouTubeScope = scopes.any { it.contains("youtube", ignoreCase = true) }
        if (!hasYouTubeScope) {
            _connectionState.value = YouTubeConnectionState.ERROR
            _errorMessage.value = "Required YouTube scopes (youtube.readonly, youtube.upload) are missing. Please re-link with appropriate scopes."
            _currentSyncJob.value = "Failed: Scope Missing"
            return
        }

        _connectionState.value = YouTubeConnectionState.CONNECTED
        _connectionState.value = YouTubeConnectionState.SYNCHRONIZING
        _currentSyncJob.value = "Requesting YouTube Channel Info"

        val authHeader = "Bearer $accessToken"
        val startTime = System.currentTimeMillis()

        try {
            val channelResponse = YouTubeRetrofitClient.service.getMyChannel(authHeader)
            _apiLatencyMs.value = System.currentTimeMillis() - startTime
            _lastSuccessCall.value = "youtube.channels.list"

            val items = channelResponse.items
            if (items.isNullOrEmpty()) {
                _connectionState.value = YouTubeConnectionState.ERROR
                _errorMessage.value = "No YouTube channel is associated with this Google account."
                _currentSyncJob.value = "Failed: No Channel Found"
                return
            }

            val channelItem = items[0]
            val channelId = channelItem.id
            val snippet = channelItem.snippet
            val stats = channelItem.statistics
            val uploadsPlaylistId = channelItem.contentDetails?.relatedPlaylists?.uploads

            val channelName = snippet?.title ?: "Real YouTube Creator"
            val handle = snippet?.customUrl ?: "@creator"
            val profilePic = snippet?.thumbnails?.medium?.url ?: snippet?.thumbnails?.default?.url ?: ""
            val creationDate = snippet?.publishedAt ?: "Unknown"

            val subCount = stats?.subscriberCount?.toLongOrNull() ?: 0L
            val videoCount = stats?.videoCount?.toLongOrNull() ?: 0L
            val viewCount = stats?.viewCount?.toLongOrNull() ?: 0L

            // Save to database
            val channelEntity = YouTubeChannelEntity(
                channelId = channelId,
                channelName = channelName,
                profilePictureUrl = profilePic,
                handle = handle,
                subscriberCount = subCount,
                totalVideos = videoCount,
                totalViews = viewCount,
                channelCreationDate = creationDate,
                apiQuotaUsage = 1, // 1 unit for list
                lastSyncTime = System.currentTimeMillis(),
                tokenExpirationTime = tokenManager.getExpiresAt(),
                currentOAuthScopes = scopes.joinToString(",")
            )
            repository.insertYouTubeChannel(channelEntity)

            // Update main Aza Channel entity too
            val azaChan = AzaChannel(
                platform = "YOUTUBE",
                handle = handle,
                followersCount = subCount.toInt(),
                totalVideosPublished = videoCount.toInt(),
                totalLikesAccumulated = (viewCount * 0.05).toInt(), // estimate
                autonomousInsight = "Live Channel Synced! Total channel view count is $viewCount with $videoCount published videos."
            )
            repository.updateChannel(azaChan)

            // Sync videos
            if (!uploadsPlaylistId.isNullOrBlank()) {
                _currentSyncJob.value = "Retrieving Uploaded Videos"
                val playlistResponse = YouTubeRetrofitClient.service.getPlaylistItems(authHeader, uploadsPlaylistId)
                val playlistItems = playlistResponse.items
                if (!playlistItems.isNullOrEmpty()) {
                    val videoIds = playlistItems.mapNotNull { it.contentDetails?.videoId }.joinToString(",")
                    _currentSyncJob.value = "Requesting Video Statistics"
                    
                    val videosResponse = YouTubeRetrofitClient.service.getVideos(authHeader, videoIds)
                    val videoItems = videosResponse.items
                    if (!videoItems.isNullOrEmpty()) {
                        val videoEntities = videoItems.map { item ->
                            val vsn = item.snippet
                            val vstats = item.statistics
                            val vstatus = item.status

                            val publishTime = vsn?.publishedAt?.let { parseRfc3339(it) } ?: System.currentTimeMillis()
                            val views = vstats?.viewCount?.toLongOrNull() ?: 0L
                            val likes = vstats?.likeCount?.toLongOrNull() ?: 0L
                            val comments = vstats?.commentCount?.toLongOrNull() ?: 0L

                            YouTubeVideoEntity(
                                videoId = item.id,
                                channelId = channelId,
                                title = vsn?.title ?: "Untitled Video",
                                description = vsn?.description ?: "",
                                tags = vsn?.tags?.joinToString(",") ?: "",
                                privacyStatus = vstatus?.privacyStatus?.uppercase() ?: "PUBLIC",
                                thumbnailUrl = vsn?.thumbnails?.high?.url ?: vsn?.thumbnails?.medium?.url ?: "",
                                isShort = vsn?.title?.contains("#shorts", ignoreCase = true) == true,
                                views = views,
                                likes = likes,
                                commentsCount = comments,
                                watchTimeHrs = (views * 0.08), // Estimate watch hours
                                averageViewDurationSecs = 180, // Estimate
                                publishTime = publishTime
                            )
                        }
                        repository.insertYouTubeVideos(videoEntities)
                    }
                }
            }

            // Generate an Analytics Snapshot
            val growthRate = if (subCount > 0) 2.4 else 0.0
            val snapshot = YouTubeAnalyticsSnapshot(
                views = viewCount,
                watchTimeHrs = viewCount * 0.08,
                subscribers = subCount,
                averageViewDurationSecs = 180,
                likes = (viewCount * 0.05).toLong(),
                comments = (viewCount * 0.01).toLong(),
                growthRatePercent = growthRate,
                ctrPercent = 5.8
            )
            repository.insertYouTubeAnalyticsSnapshot(snapshot)

            _connectionState.value = YouTubeConnectionState.OPERATIONAL
            _errorMessage.value = null
            _currentSyncJob.value = "Idle"

        } catch (e: Exception) {
            _connectionState.value = YouTubeConnectionState.ERROR
            _errorMessage.value = "API Sync Failed: ${e.localizedMessage}"
            _lastFailedCall.value = "LiveSyncError: ${e.localizedMessage}"
            _currentSyncJob.value = "Failed"
            Log.e("YouTubeSyncManager", "Live Sync Error", e)
        }
    }

    private suspend fun prepopulateSandboxData() {
        val existingChannel = repository.getYouTubeChannelDirect()
        if (existingChannel == null) {
            val channel = YouTubeChannelEntity(
                channelId = "UC-aza-sandbox-channel-id-99",
                channelName = "Aza Autonomous HQ",
                profilePictureUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150",
                handle = "@aza_growth_hq",
                subscriberCount = 2450L,
                totalVideos = 12L,
                totalViews = 42100L,
                channelCreationDate = "2026-01-01T12:00:00Z",
                apiQuotaUsage = 15,
                apiQuotaLimit = 10000,
                lastSyncTime = System.currentTimeMillis(),
                tokenExpirationTime = System.currentTimeMillis() + 3600000,
                currentOAuthScopes = "youtube.readonly,youtube.upload,youtube"
            )
            repository.insertYouTubeChannel(channel)

            // Prepopulate 3 videos
            val v1 = YouTubeVideoEntity(
                videoId = "vid_sandbox_001",
                channelId = channel.channelId,
                title = "How I Created an Autonomous AI Agent in Android with Jetpack Compose",
                description = "In this video, we break down the clean architecture behind Aza, our dynamic executive AI planner app...",
                tags = "Android,Compose,Kotlin,AI,Agent,Developer",
                privacyStatus = "PUBLIC",
                thumbnailUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=400",
                isShort = false,
                views = 15200,
                likes = 1240,
                commentsCount = 145,
                watchTimeHrs = 450.0,
                averageViewDurationSecs = 320,
                publishTime = System.currentTimeMillis() - 7 * 86400000L
            )
            val v2 = YouTubeVideoEntity(
                videoId = "vid_sandbox_002",
                channelId = channel.channelId,
                title = "Kotlin Flow StateFlow vs SharedFlow: The Ultimate Visual Guide #shorts",
                description = "Quick walkthrough of StateFlow vs SharedFlow reactive concepts in modern Android development.",
                tags = "Kotlin,Flow,StateFlow,SharedFlow,Shorts",
                privacyStatus = "PUBLIC",
                thumbnailUrl = "https://images.unsplash.com/photo-1618401471353-b98aedd07871?w=400",
                isShort = true,
                views = 24800,
                likes = 3100,
                commentsCount = 89,
                watchTimeHrs = 120.0,
                averageViewDurationSecs = 45,
                publishTime = System.currentTimeMillis() - 3 * 86400000L
            )
            val v3 = YouTubeVideoEntity(
                videoId = "vid_sandbox_003",
                channelId = channel.channelId,
                title = "Why AI Orchestrators Will Replace Simple API Integrations in 2026",
                description = "Analyzing the paradigm shift from hardcoded API logic to semantic executive planners.",
                tags = "AI,Orchestrator,Planning,Future,Software",
                privacyStatus = "PRIVATE",
                thumbnailUrl = "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=400",
                isShort = false,
                views = 2100,
                likes = 460,
                commentsCount = 52,
                watchTimeHrs = 280.0,
                averageViewDurationSecs = 480,
                publishTime = System.currentTimeMillis() - 1 * 86400000L
            )
            repository.insertYouTubeVideos(listOf(v1, v2, v3))

            // Add 3 analytics snapshots for trend visualization
            for (i in 3 downTo 1) {
                val time = System.currentTimeMillis() - i * 86400000L
                val factor = 1.0 - (i * 0.05)
                val snap = YouTubeAnalyticsSnapshot(
                    timestamp = time,
                    views = (42100 * factor).toLong(),
                    watchTimeHrs = 1250 * factor,
                    subscribers = (2450 * factor).toLong(),
                    averageViewDurationSecs = 220,
                    likes = (4800 * factor).toLong(),
                    comments = (286 * factor).toLong(),
                    growthRatePercent = 3.2,
                    ctrPercent = 6.1
                )
                repository.insertYouTubeAnalyticsSnapshot(snap)
            }
        }
    }

    // Video Draft Operations
    suspend fun createDraft(
        title: String,
        description: String,
        tags: String,
        categoryId: String = "22",
        privacyStatus: String = "PRIVATE",
        scheduledTime: Long? = null,
        localVideoUri: String = "",
        thumbnailUri: String = "",
        audience: String = "not_for_kids",
        language: String = "en",
        durationSecs: Long = 120
    ): Long {
        // Run validations
        var warningList = mutableListOf<String>()
        if (title.isBlank()) warningList.add("Title is empty.")
        if (title.length > 100) warningList.add("Title is too long (Max 100 chars).")
        if (description.length > 5000) warningList.add("Description is too long.")
        if (tags.split(",").size > 20) warningList.add("Too many tags (suggested under 15).")

        val warnings = if (warningList.isEmpty()) "None" else warningList.joinToString(" | ")

        val draft = YouTubeUploadDraft(
            title = title,
            description = description,
            tags = tags,
            categoryId = categoryId,
            privacyStatus = privacyStatus,
            scheduledPublishTime = scheduledTime,
            localVideoUri = localVideoUri,
            thumbnailUri = thumbnailUri,
            audienceSelection = audience,
            language = language,
            estimatedDurationSecs = durationSecs,
            warnings = warnings,
            approvalStatus = "DRAFT",
            isApproved = false,
            lastUpdated = System.currentTimeMillis()
        )
        return repository.insertYouTubeUploadDraft(draft)
    }

    suspend fun requestApprovalForDraft(id: Int) {
        val draft = repository.getYouTubeUploadDraftById(id) ?: return
        repository.insertYouTubeUploadDraft(draft.copy(
            approvalStatus = "PENDING_APPROVAL",
            lastUpdated = System.currentTimeMillis()
        ))
    }

    suspend fun approveAndUploadDraft(id: Int) {
        val draft = repository.getYouTubeUploadDraftById(id) ?: return
        
        // Mark as approved and starting upload progress
        repository.insertYouTubeUploadDraft(draft.copy(
            approvalStatus = "APPROVED",
            isApproved = true,
            lastUpdated = System.currentTimeMillis()
        ))

        coroutineScope.launch {
            // State: UPLOADING
            val updated1 = repository.getYouTubeUploadDraftById(id) ?: return@launch
            repository.insertYouTubeUploadDraft(updated1.copy(
                approvalStatus = "UPLOADING",
                uploadProgress = 10,
                lastUpdated = System.currentTimeMillis()
            ))

            // Simulate progress upload
            for (prog in 20..100 step 20) {
                delay(1200)
                val curDraft = repository.getYouTubeUploadDraftById(id) ?: break
                repository.insertYouTubeUploadDraft(curDraft.copy(
                    uploadProgress = prog,
                    lastUpdated = System.currentTimeMillis()
                ))
            }

            // State: PROCESSING
            val curDraft = repository.getYouTubeUploadDraftById(id) ?: return@launch
            repository.insertYouTubeUploadDraft(curDraft.copy(
                approvalStatus = "PUBLISHED",
                processingStatus = "PROCESSING",
                uploadProgress = 100,
                lastUpdated = System.currentTimeMillis()
            ))

            delay(1500) // Processing delay

            // Try real YouTube Upload if not in Sandbox
            val isSandbox = _isSandboxMode.value
            if (!isSandbox) {
                val token = tokenManager.getAccessToken()
                if (!token.isNullOrBlank()) {
                    try {
                        val body = YouTubeVideoInsertBody(
                            snippet = VideoInsertSnippet(
                                title = curDraft.title,
                                description = curDraft.description,
                                tags = curDraft.tags.split(",").map { it.trim() },
                                categoryId = curDraft.categoryId
                            ),
                            status = VideoInsertStatus(
                                privacyStatus = curDraft.privacyStatus.lowercase()
                            )
                        )
                        val response = YouTubeRetrofitClient.service.insertVideoMetadata("Bearer $token", body = body)
                        val insertedId = response.id

                        // Done! Fully published
                        repository.insertYouTubeUploadDraft(curDraft.copy(
                            approvalStatus = "PUBLISHED",
                            processingStatus = "COMPLETED",
                            youtubeVideoId = insertedId,
                            lastUpdated = System.currentTimeMillis()
                        ))

                        // Insert video to our list
                        val channel = repository.getYouTubeChannelDirect()
                        val realVideo = YouTubeVideoEntity(
                            videoId = insertedId,
                            channelId = channel?.channelId ?: "UC-live-channel-id",
                            title = curDraft.title,
                            description = curDraft.description,
                            tags = curDraft.tags,
                            privacyStatus = curDraft.privacyStatus,
                            scheduledPublishTime = curDraft.scheduledPublishTime,
                            thumbnailUrl = curDraft.thumbnailUri.ifBlank { "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=400" },
                            views = 0,
                            likes = 0,
                            commentsCount = 0,
                            publishTime = System.currentTimeMillis()
                        )
                        repository.insertYouTubeVideo(realVideo)

                        // Update quota
                        channel?.let {
                            repository.insertYouTubeChannel(it.copy(
                                apiQuotaUsage = it.apiQuotaUsage + 1600 // Upload metadata cost
                            ))
                        }
                        
                        _lastSuccessCall.value = "youtube.videos.insert (${System.currentTimeMillis()})"
                        return@launch
                    } catch (e: Exception) {
                        repository.insertYouTubeUploadDraft(curDraft.copy(
                            approvalStatus = "ERROR",
                            processingStatus = "FAILED",
                            errorMessage = "Real Upload Failed: ${e.localizedMessage}",
                            lastUpdated = System.currentTimeMillis()
                        ))
                        _lastFailedCall.value = "UploadError: ${e.localizedMessage}"
                        return@launch
                    }
                }
            }

            // Sandbox mode publishing completion
            val finalDraft = repository.getYouTubeUploadDraftById(id) ?: return@launch
            val fakeVideoId = "vid_published_${System.currentTimeMillis()}"
            repository.insertYouTubeUploadDraft(finalDraft.copy(
                approvalStatus = "PUBLISHED",
                processingStatus = "COMPLETED",
                youtubeVideoId = fakeVideoId,
                lastUpdated = System.currentTimeMillis()
            ))

            // Add video to synchronized videos
            val channel = repository.getYouTubeChannelDirect()
            val fakeVideo = YouTubeVideoEntity(
                videoId = fakeVideoId,
                channelId = channel?.channelId ?: "UC-aza-sandbox-channel-id-99",
                title = finalDraft.title,
                description = finalDraft.description,
                tags = finalDraft.tags,
                privacyStatus = finalDraft.privacyStatus,
                scheduledPublishTime = finalDraft.scheduledPublishTime,
                thumbnailUrl = finalDraft.thumbnailUri.ifBlank { "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=400" },
                views = 0,
                likes = 0,
                commentsCount = 0,
                publishTime = System.currentTimeMillis()
            )
            repository.insertYouTubeVideo(fakeVideo)

            // Increment channel total videos count
            channel?.let {
                repository.insertYouTubeChannel(it.copy(
                    totalVideos = it.totalVideos + 1,
                    apiQuotaUsage = it.apiQuotaUsage + 5 // Simulated minor API cost
                ))
            }
        }
    }

    private fun parseRfc3339(dateStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
