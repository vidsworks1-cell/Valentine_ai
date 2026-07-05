package com.example.aza.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureCredentialStore {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "AzaSecureKeyAlias"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "aza_secure_prefs"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        getOrCreateSecretKey()
    }

    private fun getContext(): Context {
        return appContext ?: throw IllegalStateException("SecureCredentialStore has not been initialized with Context")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encrypt(key: String, value: String) {
        val context = getContext()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encryptionIv = cipher.iv
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

            val ivStr = Base64.encodeToString(encryptionIv, Base64.DEFAULT)
            val encryptedStr = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("${key}_iv", ivStr)
                .putString("${key}_val", encryptedStr)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun decrypt(key: String): String? {
        val context = getContext()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ivStr = prefs.getString("${key}_iv", null) ?: return null
        val encryptedStr = prefs.getString("${key}_val", null) ?: return null

        return try {
            val iv = Base64.decode(ivStr, Base64.DEFAULT)
            val encryptedBytes = Base64.decode(encryptedStr, Base64.DEFAULT)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun delete(key: String) {
        val context = getContext()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${key}_iv")
            .remove("${key}_val")
            .apply()
    }

    // Helper functions for specific fields to guarantee type safety & structure

    // --- Binance Credentials ---
    fun saveBinanceCredentials(apiKey: String, secretKey: String, env: String, tradingMode: String) {
        encrypt("binance_api_key", apiKey)
        encrypt("binance_api_secret", secretKey)
        encrypt("binance_env", env)
        encrypt("binance_trading_mode", tradingMode)
    }

    fun getBinanceApiKey(): String = decrypt("binance_api_key") ?: ""
    fun getBinanceApiSecret(): String = decrypt("binance_api_secret") ?: ""
    fun getBinanceEnv(): String = decrypt("binance_env") ?: "Sandbox" // Sandbox or Production
    fun getBinanceTradingMode(): String = decrypt("binance_trading_mode") ?: "Read-Only" // Read-Only or Trading Mode

    fun deleteBinanceCredentials() {
        delete("binance_api_key")
        delete("binance_api_secret")
        delete("binance_env")
        delete("binance_trading_mode")
    }

    // --- Google / YouTube Credentials ---
    fun saveYouTubeCredentials(token: String, refreshToken: String, channelId: String, channelName: String, subscribers: Int, views: Int, videos: Int, playlists: Int) {
        encrypt("youtube_oauth_token", token)
        encrypt("youtube_refresh_token", refreshToken)
        encrypt("youtube_channel_id", channelId)
        encrypt("youtube_channel_name", channelName)
        encrypt("youtube_subscribers", subscribers.toString())
        encrypt("youtube_views", views.toString())
        encrypt("youtube_videos", videos.toString())
        encrypt("youtube_playlists", playlists.toString())
    }

    fun getYouTubeOAuthToken(): String = decrypt("youtube_oauth_token") ?: ""
    fun getYouTubeRefreshToken(): String = decrypt("youtube_refresh_token") ?: ""
    fun getYouTubeChannelId(): String = decrypt("youtube_channel_id") ?: ""
    fun getYouTubeChannelName(): String = decrypt("youtube_channel_name") ?: ""
    fun getYouTubeSubscribers(): Int = decrypt("youtube_subscribers")?.toIntOrNull() ?: 0
    fun getYouTubeViews(): Int = decrypt("youtube_views")?.toIntOrNull() ?: 0
    fun getYouTubeVideos(): Int = decrypt("youtube_videos")?.toIntOrNull() ?: 0
    fun getYouTubePlaylists(): Int = decrypt("youtube_playlists")?.toIntOrNull() ?: 0

    fun deleteYouTubeCredentials() {
        delete("youtube_oauth_token")
        delete("youtube_refresh_token")
        delete("youtube_channel_id")
        delete("youtube_channel_name")
        delete("youtube_subscribers")
        delete("youtube_views")
        delete("youtube_videos")
        delete("youtube_playlists")
    }

    // --- GitHub Credentials ---
    fun saveGitHubCredentials(token: String, username: String, repo: String, branch: String) {
        encrypt("github_oauth_token", token)
        encrypt("github_username", username)
        encrypt("github_repo", repo)
        encrypt("github_branch", branch)
    }

    fun getGitHubOAuthToken(): String = decrypt("github_oauth_token") ?: ""
    fun getGitHubUsername(): String = decrypt("github_username") ?: ""
    fun getGitHubRepo(): String = decrypt("github_repo") ?: ""
    fun getGitHubBranch(): String = decrypt("github_branch") ?: "main"

    fun deleteGitHubCredentials() {
        delete("github_oauth_token")
        delete("github_username")
        delete("github_repo")
        delete("github_branch")
    }

    // --- OpenAI / Gemini Credentials ---
    fun saveOpenAICredentials(apiKey: String) {
        encrypt("openai_gemini_api_key", apiKey)
    }

    fun getOpenAICredentials(): String = decrypt("openai_gemini_api_key") ?: ""

    fun deleteOpenAICredentials() {
        delete("openai_gemini_api_key")
    }
}
