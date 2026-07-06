package com.example.aza.cognition

object ClarificationEngine {

    fun determineClarificationQuestion(text: String, intent: IntentObject): String? {
        val lower = text.lowercase()
        
        if (intent.primaryIntent == "TRADING_EXECUTION") {
            val hasSymbol = lower.contains("btc") || lower.contains("eth") || lower.contains("aapl") || lower.contains("nvda")
            val hasAmount = lower.contains(Regex("""\d"""))
            
            if (!hasSymbol) {
                return "Which asset would you like me to evaluate or trade? (BTC, ETH, AAPL, or NVDA)"
            }
            if (!hasAmount) {
                return "Understood. How many units of the asset or what amount in cash would you like me to allocate?"
            }
        }
        
        if (intent.primaryIntent == "CONTENT_PLANNING") {
            val hasPlatform = lower.contains("tiktok") || lower.contains("youtube") || lower.contains("instagram")
            if (!hasPlatform) {
                return "Which platform should I target for this script? (YouTube, TikTok, or Instagram)"
            }
        }
        
        return null
    }
}
