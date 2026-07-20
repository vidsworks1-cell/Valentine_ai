package com.example.aza.intelligence

import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.database.KnowledgeEntry
import com.example.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class EvaluationResult(
    val title: String,
    val source: String,
    val credibilityScore: Double,
    val relevanceScore: Double,
    val modelImprovementScore: Double,
    val overallScore: Double,
    val status: String, // "ACCEPTED", "REJECTED"
    val report: String
)

object ExternalEvidenceEvaluator {

    private const val DEFAULT_MODEL = "gemini-3.5-flash"

    /**
     * Evaluates external evidence to determine whether it should be integrated into the Knowledge Base.
     */
    suspend fun evaluateEvidence(
        title: String,
        sourceType: String, // "Academic Paper", "Exchange API", "Market Regulation", "Market Shift"
        content: String,
        repository: AppRepository
    ): EvaluationResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val hasApiKey = apiKey.isNotEmpty() && 
                        apiKey != "MY_GEMINI_API_KEY" && 
                        !apiKey.contains("placeholder", ignoreCase = true)

        if (hasApiKey) {
            try {
                return@withContext evaluateWithGemini(title, sourceType, content, apiKey)
            } catch (e: Exception) {
                // Fall back to local heuristic evaluator if API call fails
                return@withContext evaluateWithHeuristics(title, sourceType, content)
            }
        } else {
            return@withContext evaluateWithHeuristics(title, sourceType, content)
        }
    }

    private suspend fun evaluateWithGemini(
        title: String,
        sourceType: String,
        content: String,
        apiKey: String
    ): EvaluationResult {
        val prompt = """
            You are Lumina's cognitive evaluation agent. Your job is to strictly evaluate the following external evidence.
            We DO NOT automatically accept external information. It must be credible, highly relevant to our trading model (quantitative trading, crypto/equity spot/futures, order flow, technical indicators, dynamic stops), and actually improve our model.
            
            EVALUATION DIRECTIVE:
            1. Determine a Credibility Score (0.00 to 1.00):
               - Peer-reviewed academic journals, registered regulators (SEC, CFTC), or primary exchange API documents (Binance, Coinbase) are high credibility (0.80 - 1.00).
               - Random medium articles, anonymous tweets, or speculative blogs are low credibility (0.10 - 0.50).
            2. Determine a Relevance Score (0.00 to 1.00):
               - Direct trading mechanics, risk management, order flow, slippage, and volatility on our assets (BTC, ETH, etc.) are highly relevant (0.80 - 1.00).
               - Broad macro topics or unrelated systems are low relevance (0.10 - 0.50).
            3. Determine a Model Improvement Score (0.00 to 1.00):
               - Can we use this to optimize our current strategies (Trend Following, Breakout, Mean Reversion)? Does it offer actionable rules (e.g. dynamic stops, latency mitigation, volume filters)?
            4. Decide Status ("ACCEPTED" or "REJECTED"):
               - Average of the three scores must be at least 0.75 for ACCEPTED status.
               - If any score is dangerously low (e.g., credibility below 0.60), reject it!
            
            EVIDENCE TO EVALUATE:
            - Title: $title
            - Type: $sourceType
            - Content: $content
            
            RESPONSE FORMAT:
            You must format your response exactly as follows:
            CREDIBILITY: [double score e.g. 0.85]
            RELEVANCE: [double score e.g. 0.90]
            IMPROVEMENT: [double score e.g. 0.80]
            STATUS: [ACCEPTED or REJECTED]
            REPORT:
            [Insert a concise professional markdown report detailing the strengths, gaps, relevance mapping, and a explicit recommendation of whether/how to incorporate this into our active models.]
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        val response = RetrofitClient.service.generateContent(DEFAULT_MODEL, apiKey, request)
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
            ?: throw Exception("No response from Gemini API")

        return parseGeminiResponse(title, sourceType, text)
    }

    private fun parseGeminiResponse(title: String, sourceType: String, text: String): EvaluationResult {
        var credibility = 0.5
        var relevance = 0.5
        var improvement = 0.5
        var status = "REJECTED"
        var report = text

        val lines = text.split("\n")
        var reportIndex = -1

        for (i in lines.indices) {
            val line = lines[i].trim()
            when {
                line.startsWith("CREDIBILITY:", ignoreCase = true) -> {
                    credibility = line.substringAfter(":").trim().toDoubleOrNull() ?: 0.5
                }
                line.startsWith("RELEVANCE:", ignoreCase = true) -> {
                    relevance = line.substringAfter(":").trim().toDoubleOrNull() ?: 0.5
                }
                line.startsWith("IMPROVEMENT:", ignoreCase = true) -> {
                    improvement = line.substringAfter(":").trim().toDoubleOrNull() ?: 0.5
                }
                line.startsWith("STATUS:", ignoreCase = true) -> {
                    status = line.substringAfter(":").trim().uppercase(Locale.US)
                    if (status != "ACCEPTED" && status != "REJECTED") {
                        status = "REJECTED"
                    }
                }
                line.startsWith("REPORT:", ignoreCase = true) -> {
                    reportIndex = i
                    break
                }
            }
        }

        if (reportIndex != -1 && reportIndex < lines.size - 1) {
            report = lines.subList(reportIndex + 1, lines.size).joinToString("\n").trim()
        }

        val overall = (credibility + relevance + improvement) / 3.0
        val finalStatus = if (overall >= 0.75 && credibility >= 0.60) status else "REJECTED"

        return EvaluationResult(
            title = title,
            source = sourceType,
            credibilityScore = credibility,
            relevanceScore = relevance,
            modelImprovementScore = improvement,
            overallScore = overall,
            status = finalStatus,
            report = report
        )
    }

    /**
     * Local high-fidelity heuristic evaluator if offline or API key is missing.
     */
    fun evaluateWithHeuristics(title: String, sourceType: String, content: String): EvaluationResult {
        val titleLower = title.lowercase(Locale.US)
        val contentLower = content.lowercase(Locale.US)

        // Heuristics for Credibility based on academic, official API, or regulation terms
        var credibility = 0.50
        val credibilityTerms = listOf("journal", "microstructure", "empirical", "university", "sec", "cftc", "binance api", "documentation", "whitepaper", "research", "regulatory")
        val matchCountCred = credibilityTerms.count { titleLower.contains(it) || contentLower.contains(it) }
        credibility += (matchCountCred * 0.10)
        if (sourceType == "Academic Paper" || sourceType == "Exchange API" || sourceType == "Market Regulation") {
            credibility += 0.15
        }
        credibility = credibility.coerceIn(0.1, 1.0)

        // Heuristics for Relevance to quantitative trading and digital assets
        var relevance = 0.40
        val relevanceTerms = listOf("order flow", "slippage", "latency", "limit order", "market maker", "liquidity", "btc", "eth", "futures", "volatility", "atr", "rsi", "reversal")
        val matchCountRel = relevanceTerms.count { titleLower.contains(it) || contentLower.contains(it) }
        relevance += (matchCountRel * 0.12)
        relevance = relevance.coerceIn(0.1, 1.0)

        // Heuristics for Model Improvement (actionable parameters)
        var improvement = 0.35
        val improvementTerms = listOf("optimize", "dynamic stop", "reduce risk", "win rate", "alpha", "prediction", "parameter tuning", "calibration", "filter fake", "regime switch")
        val matchCountImp = improvementTerms.count { titleLower.contains(it) || contentLower.contains(it) }
        improvement += (matchCountImp * 0.12)
        improvement = improvement.coerceIn(0.1, 1.0)

        val overall = (credibility + relevance + improvement) / 3.0
        val isAccepted = overall >= 0.75 && credibility >= 0.60
        val status = if (isAccepted) "ACCEPTED" else "REJECTED"

        // Generate custom report depending on type and status
        val reportBuilder = StringBuilder()
        reportBuilder.append("### Lumina Local Cognitive Evaluation Report\n\n")
        reportBuilder.append("• **Analysis Profile**: Heuristics Engine Evaluator (Local Mode)\n")
        reportBuilder.append("• **Target Evidence**: \"$title\" ($sourceType)\n")
        reportBuilder.append("• **Evaluation Verdict**: **$status** (Overall score: ${String.format(Locale.US, "%.1f", overall * 100.0)}%)\n\n")

        reportBuilder.append("#### Scoring Breakdown:\n")
        reportBuilder.append("- **Credibility**: ${String.format(Locale.US, "%.2f", credibility)} - Evaluated based on official, academic, or primary exchange documentation terms.\n")
        reportBuilder.append("- **Relevance**: ${String.format(Locale.US, "%.2f", relevance)} - Gauged relative to crypto execution parameters and indicator layers.\n")
        reportBuilder.append("- **Model Improvement Potential**: ${String.format(Locale.US, "%.2f", improvement)} - Measures ability to recalibrate order-placement, spread, or stops.\n\n")

        reportBuilder.append("#### Analytical Insight:\n")
        if (isAccepted) {
            reportBuilder.append("This evidence provides highly structured, actionable details. The terminology correlates with current market dynamics (matched keys: ${relevanceTerms.filter { titleLower.contains(it) || contentLower.contains(it) }.take(4).joinToString(", ")}). ")
            reportBuilder.append("Integrating this into AZA's Knowledge Hub is recommended to recalibrate signal thresholds or buffer dynamic slippage tolerances.")
        } else {
            reportBuilder.append("This information failed to meet our strict quality and credibility benchmarks. ")
            if (credibility < 0.60) {
                reportBuilder.append("The source lacks verified academic rigor or institutional authority, posing an over-fitting or noise hazard to our trading model. ")
            } else {
                reportBuilder.append("While the source is credible, the concepts are too generic or lack actionable mathematical or strategic parameters to improve our existing active strategies (Trend Following, Breakout, Mean Reversion). ")
            }
            reportBuilder.append("This record has been safely logged but is excluded from our active routing knowledge.")
        }

        return EvaluationResult(
            title = title,
            source = sourceType,
            credibilityScore = credibility,
            relevanceScore = relevance,
            modelImprovementScore = improvement,
            overallScore = overall,
            status = status,
            report = reportBuilder.toString()
        )
    }

    /**
     * Incorporates accepted evidence as a high-confidence KnowledgeEntry in the database.
     */
    suspend fun integrateIntoKnowledgeHub(
        result: EvaluationResult,
        repository: AppRepository
    ) {
        if (result.status != "ACCEPTED") return

        val kbEntry = KnowledgeEntry(
            title = "External Evidence: ${result.title}",
            category = "External Evidence",
            description = "Analyzed and accepted ${result.source}. report: ${result.report.take(400)}...",
            evidence = "Source Type: ${result.source}. Credibility: ${String.format(Locale.US, "%.2f", result.credibilityScore)}, Relevance: ${String.format(Locale.US, "%.2f", result.relevanceScore)}, Model Improvement: ${String.format(Locale.US, "%.2f", result.modelImprovementScore)}.",
            confidenceScore = result.overallScore,
            timesConfirmed = 1,
            isConfirmed = true
        )
        repository.insertKnowledge(kbEntry)
    }
}
