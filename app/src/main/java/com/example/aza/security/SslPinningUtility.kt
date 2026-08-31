package com.example.aza.security

import okhttp3.CertificatePinner

object SslPinningUtility {
    /**
     * Optional CertificatePinner for external API connections.
     * Uses standard SSL validation by default so rotated exchange certificates
     * or dynamic edge CDNs do not cause SSLPeerUnverifiedException.
     */
    val certificatePinner: CertificatePinner by lazy {
        CertificatePinner.Builder().build()
    }
}
