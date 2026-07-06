package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface YouTubeApiService {
    @GET("youtube/v3/channels")
    suspend fun getMyChannel(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,statistics,contentDetails",
        @Query("mine") mine: Boolean = true
    ): YouTubeChannelResponse

    @GET("youtube/v3/playlistItems")
    suspend fun getPlaylistItems(
        @Header("Authorization") authHeader: String,
        @Query("playlistId") playlistId: String,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("maxResults") maxResults: Int = 20
    ): YouTubePlaylistItemsResponse

    @GET("youtube/v3/videos")
    suspend fun getVideos(
        @Header("Authorization") authHeader: String,
        @Query("id") ids: String,
        @Query("part") part: String = "snippet,statistics,status"
    ): YouTubeVideosResponse

    @POST("youtube/v3/videos")
    suspend fun insertVideoMetadata(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,status",
        @Body body: YouTubeVideoInsertBody
    ): YouTubeVideoInsertResponse
}

// Response data classes for YouTube Data API v3

data class YouTubeChannelResponse(
    val items: List<ChannelItem>?
)

data class ChannelItem(
    val id: String,
    val snippet: ChannelSnippet?,
    val statistics: ChannelStatistics?,
    val contentDetails: ChannelContentDetails?
)

data class ChannelSnippet(
    val title: String,
    val description: String,
    val customUrl: String?,
    val publishedAt: String,
    val thumbnails: ThumbnailsMap?
)

data class ThumbnailsMap(
    val default: ThumbnailInfo?,
    val medium: ThumbnailInfo?,
    val high: ThumbnailInfo?
)

data class ThumbnailInfo(
    val url: String
)

data class ChannelStatistics(
    val viewCount: String?,
    val subscriberCount: String?,
    val videoCount: String?
)

data class ChannelContentDetails(
    val relatedPlaylists: RelatedPlaylists?
)

data class RelatedPlaylists(
    val uploads: String?
)

data class YouTubePlaylistItemsResponse(
    val items: List<PlaylistItem>?
)

data class PlaylistItem(
    val id: String,
    val snippet: PlaylistItemSnippet?,
    val contentDetails: PlaylistItemContentDetails?
)

data class PlaylistItemSnippet(
    val publishedAt: String,
    val channelId: String,
    val title: String,
    val description: String,
    val thumbnails: ThumbnailsMap?
)

data class PlaylistItemContentDetails(
    val videoId: String,
    val videoPublishedAt: String?
)

data class YouTubeVideosResponse(
    val items: List<VideoItem>?
)

data class VideoItem(
    val id: String,
    val snippet: VideoSnippet?,
    val statistics: VideoStatistics?,
    val status: VideoStatus?
)

data class VideoSnippet(
    val publishedAt: String,
    val channelId: String,
    val title: String,
    val description: String,
    val thumbnails: ThumbnailsMap?,
    val tags: List<String>?,
    val categoryId: String?
)

data class VideoStatistics(
    val viewCount: String?,
    val likeCount: String?,
    val commentCount: String?
)

data class VideoStatus(
    val uploadStatus: String?,
    val privacyStatus: String?,
    val license: String?
)

data class YouTubeVideoInsertBody(
    val snippet: VideoInsertSnippet,
    val status: VideoInsertStatus
)

data class VideoInsertSnippet(
    val title: String,
    val description: String,
    val tags: List<String>?,
    val categoryId: String = "22"
)

data class VideoInsertStatus(
    val privacyStatus: String, // private, public, unlisted
    val selfDeclaredMadeForKids: Boolean = false
)

data class YouTubeVideoInsertResponse(
    val id: String,
    val snippet: VideoSnippet?,
    val status: VideoStatus?
)

object YouTubeRetrofitClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: YouTubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubeApiService::class.java)
    }
}
