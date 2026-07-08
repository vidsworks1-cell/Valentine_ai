package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class LaptopDocument(
    val id: String,
    val title: String,
    val content: String,
    val category: String,
    val sizeBytes: Long,
    val lastModified: Long
)

data class DataExchangePayload(
    val deviceId: String,
    val clientVersion: String,
    val dataPayload: Map<String, String>,
    val timestamp: Long
)

data class SyncResponse(
    val status: String,
    val timestamp: Long,
    val recordsSynced: Int,
    val deviceName: String
)

interface LaptopAgentApiService {
    @GET("api/documents")
    suspend fun getDocuments(): List<LaptopDocument>

    @GET("api/documents/{id}")
    suspend fun getDocumentById(@Path("id") id: String): LaptopDocument

    @POST("api/exchange")
    suspend fun exchangeData(@Body payload: DataExchangePayload): SyncResponse

    @GET("api/sync-status")
    suspend fun getSyncStatus(): SyncResponse

    @GET("api/ping")
    suspend fun ping(): Map<String, String>
}

object LaptopAgentClient {
    fun buildService(customBaseUrl: String): LaptopAgentApiService {
        // Ensure baseUrl is properly formatted
        val formattedUrl = when {
            customBaseUrl.startsWith("http://") || customBaseUrl.startsWith("https://") -> customBaseUrl
            else -> "http://$customBaseUrl"
        }.let { if (it.endsWith("/")) it else "$it/" }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .certificatePinner(com.example.aza.security.SslPinningUtility.certificatePinner)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LaptopAgentApiService::class.java)
    }
}
