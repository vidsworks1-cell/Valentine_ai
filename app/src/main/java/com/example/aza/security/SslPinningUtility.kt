package com.example.aza.security

import okhttp3.CertificatePinner

object SslPinningUtility {
    /**
     * Centralized CertificatePinner for external API connections, securing
     * transport-layer integrity against Man-in-the-Middle (MITM) attacks.
     *
     * Includes primary and multiple backup pins for:
     * - Binance API (api.binance.com)
     */
    val certificatePinner: CertificatePinner by lazy {
        CertificatePinner.Builder()
            // --- Binance API Certificate Pins (including backup intermediate/root CAs) ---
            .add("api.binance.com", "sha256//Y6BOeqMgXS6wjqk6emFs+Y+HWkIXO2R8Dox5VO1YT0=") // Primary Leaf
            .add("api.binance.com", "sha256/M8HztJeS69asYQs7S3M/9r6BdfD2S90Z80S1a3jU0L8=") // Intermediate backup
            .add("api.binance.com", "sha256/k2v657W2gCwcstQJW6M979G581b89N9635078A81b9A=") // Root backup

            .build()
    }
}
