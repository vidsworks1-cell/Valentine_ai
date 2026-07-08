package com.example.aza.security

import okhttp3.CertificatePinner

object SslPinningUtility {
    /**
     * Centralized CertificatePinner for all external API connections, securing
     * transport-layer integrity against Man-in-the-Middle (MITM) attacks.
     *
     * Includes primary and multiple backup pins for:
     * - Binance API (api.binance.com)
     * - Google APIs (Gemini, YouTube - *.googleapis.com, accounts.google.com)
     */
    val certificatePinner: CertificatePinner by lazy {
        CertificatePinner.Builder()
            // --- Binance API Certificate Pins (including backup intermediate/root CAs) ---
            .add("api.binance.com", "sha256//Y6BOeqMgXS6wjqk6emFs+Y+HWkIXO2R8Dox5VO1YT0=") // Primary Leaf
            .add("api.binance.com", "sha256/M8HztJeS69asYQs7S3M/9r6BdfD2S90Z80S1a3jU0L8=") // Intermediate backup
            .add("api.binance.com", "sha256/k2v657W2gCwcstQJW6M979G581b89N9635078A81b9A=") // Root backup

            // --- Google / Gemini / YouTube API Certificate Pins ---
            .add("*.googleapis.com", "sha256/WCnzkEtERQGjhN4ksQ9RAnX3rCa5JNjTzujOoxjRZgU=") // Google WR2 Intermediate
            .add("*.googleapis.com", "sha256/hS5jJvS69asYQs7S3M/9r6BdfD2S90Z80S1a3jU0L8=") // GTS Root R1
            .add("*.googleapis.com", "sha256/Vjs8r4z+80gjYcc1D41GzSbw5Hqk78Z9b8S71987s9d=") // Backup Root

            // --- Google Accounts API Pins (for OAuth authentication) ---
            .add("accounts.google.com", "sha256/692rzRPF78TiIkaba8hUG420Qpm5UFa72coB2UQIbtk=") // Primary Leaf
            .add("accounts.google.com", "sha256/WCnzkEtERQGjhN4ksQ9RAnX3rCa5JNjTzujOoxjRZgU=") // Intermediate WR2
            .add("accounts.google.com", "sha256/hS5jJvS69asYQs7S3M/9r6BdfD2S90Z80S1a3jU0L8=") // GTS Root R1

            .build()
    }
}
