package com.example.aza.authentication

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class GoogleTokenManager(private val context: Context) {
    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "AzaGoogleAuthKeyAlias"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "aza_google_auth_secure_prefs"

        private const val KEY_ACCESS_TOKEN = "google_access_token"
        private const val KEY_REFRESH_TOKEN = "google_refresh_token"
        private const val KEY_ID_TOKEN = "google_id_token"
        private const val KEY_EXPIRES_AT = "google_expires_at"
        private const val KEY_EMAIL = "google_email"
        private const val KEY_DISPLAY_NAME = "google_display_name"
        private const val KEY_PROFILE_PIC = "google_profile_pic"
        private const val KEY_SCOPES = "google_scopes"
        private const val KEY_LAST_SYNC = "google_last_sync"
    }

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    private fun encrypt(key: String, value: String?) {
        if (value == null) {
            sharedPreferences.edit()
                .remove("${key}_iv")
                .remove("${key}_val")
                .apply()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encryptionIv = cipher.iv
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

            val ivStr = Base64.encodeToString(encryptionIv, Base64.DEFAULT)
            val encryptedStr = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)

            sharedPreferences.edit()
                .putString("${key}_iv", ivStr)
                .putString("${key}_val", encryptedStr)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun decrypt(key: String): String? {
        val ivStr = sharedPreferences.getString("${key}_iv", null) ?: return null
        val encryptedStr = sharedPreferences.getString("${key}_val", null) ?: return null

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

    fun saveTokens(accessToken: String?, refreshToken: String?, idToken: String?, expiresInSeconds: Long) {
        encrypt(KEY_ACCESS_TOKEN, accessToken)
        if (refreshToken != null) {
            encrypt(KEY_REFRESH_TOKEN, refreshToken)
        }
        encrypt(KEY_ID_TOKEN, idToken)
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
        sharedPreferences.edit().putLong(KEY_EXPIRES_AT, expiresAt).apply()
    }

    fun getAccessToken(): String? = decrypt(KEY_ACCESS_TOKEN)
    fun getRefreshToken(): String? = decrypt(KEY_REFRESH_TOKEN)
    fun getIdToken(): String? = decrypt(KEY_ID_TOKEN)
    fun getExpiresAt(): Long = sharedPreferences.getLong(KEY_EXPIRES_AT, 0L)

    fun isTokenExpired(): Boolean {
        val expiresAt = getExpiresAt()
        // Expired if current time is within 5 minutes of the expiration time
        return System.currentTimeMillis() >= (expiresAt - 5 * 60 * 1000)
    }

    fun saveProfile(email: String, displayName: String, profilePicUrl: String, scopes: List<String>) {
        encrypt(KEY_EMAIL, email)
        encrypt(KEY_DISPLAY_NAME, displayName)
        encrypt(KEY_PROFILE_PIC, profilePicUrl)
        encrypt(KEY_SCOPES, scopes.joinToString(","))
        sharedPreferences.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    fun getEmail(): String = decrypt(KEY_EMAIL) ?: ""
    fun getDisplayName(): String = decrypt(KEY_DISPLAY_NAME) ?: ""
    fun getProfilePicUrl(): String = decrypt(KEY_PROFILE_PIC) ?: ""
    fun getGrantedScopes(): List<String> {
        val scopesStr = decrypt(KEY_SCOPES) ?: return emptyList()
        return if (scopesStr.isBlank()) emptyList() else scopesStr.split(",")
    }
    fun getLastSyncTime(): Long = sharedPreferences.getLong(KEY_LAST_SYNC, 0L)

    fun clear() {
        encrypt(KEY_ACCESS_TOKEN, null)
        encrypt(KEY_REFRESH_TOKEN, null)
        encrypt(KEY_ID_TOKEN, null)
        encrypt(KEY_EMAIL, null)
        encrypt(KEY_DISPLAY_NAME, null)
        encrypt(KEY_PROFILE_PIC, null)
        encrypt(KEY_SCOPES, null)
        sharedPreferences.edit()
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_LAST_SYNC)
            .apply()
    }

    fun hasCredentials(): Boolean {
        return getAccessToken() != null || getRefreshToken() != null || getIdToken() != null
    }
}
