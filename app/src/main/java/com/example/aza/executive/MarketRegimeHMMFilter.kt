package com.example.aza.executive

import android.util.Log
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MarketRegimeHMMFilter {
    companion object {
        private const val TAG = "MarketRegimeHMMFilter"
        
        // State indices
        const val STATE_BULL = 0
        const val STATE_BEAR = 1
        const val STATE_RANGE = 2
        const val STATE_VOLATILE = 3
        
        val STATE_NAMES = listOf("BULL", "BEAR", "RANGE", "VOLATILE")
    }

    // --- Calibrated Markov Transition Matrix (High Self-Transition Priors to reflect historical stability) ---
    private val transitionMatrix = arrayOf(
        // From: BULL, BEAR, RANGE, VOLATILE
        doubleArrayOf(0.92, 0.02, 0.05, 0.01),     // To BULL
        doubleArrayOf(0.02, 0.90, 0.05, 0.03),     // To BEAR
        doubleArrayOf(0.04, 0.03, 0.91, 0.02),     // To RANGE
        doubleArrayOf(0.02, 0.05, 0.03, 0.90)      // To VOLATILE
    )

    // --- State GMM Likelihood Means and Standard Deviations ---
    // Features: [Normalized Return, Volatility, Normalized OFI]
    private val featureMeans = arrayOf(
        doubleArrayOf(0.4, 0.012, 0.5),     // BULL: Positive return, low vol, positive OFI
        doubleArrayOf(-0.5, 0.022, -0.6),   // BEAR: Negative return, medium vol, negative OFI
        doubleArrayOf(0.0, 0.008, 0.0),     // RANGE: Flat return, very low vol, flat OFI
        doubleArrayOf(-0.1, 0.045, 0.1)     // VOLATILE: Erratic return, extremely high vol, erratic OFI
    )

    private val featureStds = arrayOf(
        doubleArrayOf(0.3, 0.005, 0.3),     // BULL
        doubleArrayOf(0.4, 0.008, 0.3),     // BEAR
        doubleArrayOf(0.1, 0.003, 0.2),     // RANGE
        doubleArrayOf(0.8, 0.015, 0.6)      // VOLATILE
    )

    // --- Filtering and Bayes States ---
    private var priorProbabilities = doubleArrayOf(0.10, 0.10, 0.70, 0.10) // Start with Range bias
    private var exponentiallySmoothedProbs = doubleArrayOf(0.10, 0.10, 0.70, 0.10)
    
    // Configurable parameters
    var exponentialSmoothingAlpha = 0.25  // Exponential filter weight (gamma)
    var medianWindowSize = 5               // Running median window size to eliminate jitter
    var infoShockThreshold = 0.65          // KL Divergence threshold for Information Shock
    
    // --- Dual-Path Parameters (Adaptive vs Invariant Paths) ---
    var fastAlpha = 0.60                  // Fast path: high-speed tracking (highly responsive alpha)
    var slowAlpha = 0.12                  // Slow path: structural stability (conservative alpha)
    var fastMedianWindow = 3              // Small window size for fast-reacting modes
    var slowMedianWindow = 12             // Large window size for solid macro states
    
    // Path-specific smoothed probability state tracking
    private var fastSmoothedProbs = doubleArrayOf(0.10, 0.10, 0.70, 0.10)
    private var slowSmoothedProbs = doubleArrayOf(0.10, 0.10, 0.70, 0.10)
    
    // Path-specific histories for independent majority-voting
    private val fastRegimeHistory = mutableListOf<String>()
    private val slowRegimeHistory = mutableListOf<String>()
    
    // Public exposure of path decisions for UI visualization and external coupling
    var fastRegime = "RANGE"
    var slowRegime = "RANGE"
    var dynamicFusionWeight = 0.25         // w: current priority given to Fast Path vs Slow Path
    
    // Rolling buffers
    private val regimeDecisionHistory = mutableListOf<String>()
    var lastInformationShockVal = 0.0
    var isInformationShockDetected = false

    /**
     * Executes the hybrid-filtered GMM-HMM regime estimator.
     * Takes multi-microstructure inputs, reduces dimensionality, runs HMM transitions,
     * performs Recursive Bayesian updating with Virtual Evidence, checks for KL Divergence shocks,
     * and filters final output through a Running Median & Exponential Weighting combination.
     */
    fun processObservation(
        returns: Double,
        volatility: Double,
        ofi: Double,
        virtualEvidenceConfidence: Double = 0.90
    ): String {
        // 1. Feature Preprocessing / Normalization (reduces dimensionality/redundancy of raw returns and OFI)
        val normReturn = returns.coerceIn(-3.0, 3.0)
        val normOfi = ofi.coerceIn(-2.0, 2.0)
        val normVol = max(0.0001, volatility)

        // 2. Compute GMM Emission Likelihoods for each state
        val emissionLikelihoods = DoubleArray(4)
        for (state in 0..3) {
            emissionLikelihoods[state] = calculateGmmLikelihood(state, normReturn, normVol, normOfi)
        }

        // Normalize emissions to represent conditional probabilities
        val likelihoodSum = emissionLikelihoods.sum()
        if (likelihoodSum > 0.0) {
            for (i in 0..3) emissionLikelihoods[i] /= likelihoodSum
        } else {
            // Fallback in case of numerical instability
            emissionLikelihoods[STATE_RANGE] = 1.0
        }

        // 3. Markov Transition Prior Projection: P(S_t | O_{t-1}) = T * P(S_{t-1})
        val transitionPriors = DoubleArray(4)
        for (toState in 0..3) {
            var sum = 0.0
            for (fromState in 0..3) {
                sum += transitionMatrix[toState][fromState] * priorProbabilities[fromState]
            }
            transitionPriors[toState] = sum
        }

        // 4. Recursive Bayesian Update with Virtual Evidence
        // Virtual Evidence modifies the update to account for uncertainties in our measurement model
        val updatedPosteriors = DoubleArray(4)
        for (state in 0..3) {
            // Apply Virtual Evidence weighting to state likelihoods
            val softLikelihood = (virtualEvidenceConfidence * emissionLikelihoods[state]) + 
                                 ((1.0 - virtualEvidenceConfidence) * 0.25)
            updatedPosteriors[state] = softLikelihood * transitionPriors[state]
        }

        // Normalize updated posterior probabilities
        val posteriorSum = updatedPosteriors.sum()
        if (posteriorSum > 0.0) {
            for (i in 0..3) updatedPosteriors[i] /= posteriorSum
        } else {
            for (i in 0..3) updatedPosteriors[i] = transitionPriors[i]
        }

        // 5. Kullback-Leibler Divergence: Measure Information Shock
        // D_KL(Posteriors || TransitionPriors)
        var klDivergence = 0.0
        for (i in 0..3) {
            val p = updatedPosteriors[i].coerceIn(1e-6, 1.0)
            val q = transitionPriors[i].coerceIn(1e-6, 1.0)
            klDivergence += p * ln(p / q)
        }
        lastInformationShockVal = klDivergence
        isInformationShockDetected = klDivergence > infoShockThreshold

        // Update the Markov prior for next step
        priorProbabilities = updatedPosteriors.clone()

        // 6. Dual-Path Probability Smoothing & Running Median Filtering
        
        // --- FAST PATH: Highly adaptive, fast tracking ---
        for (i in 0..3) {
            fastSmoothedProbs[i] = (fastAlpha * updatedPosteriors[i]) + 
                                   ((1.0 - fastAlpha) * fastSmoothedProbs[i])
        }
        val fastCandidateIdx = fastSmoothedProbs.indices.maxByOrNull { fastSmoothedProbs[it] } ?: STATE_RANGE
        val fastCandidate = STATE_NAMES[fastCandidateIdx]
        
        fastRegimeHistory.add(fastCandidate)
        if (fastRegimeHistory.size > fastMedianWindow) {
            fastRegimeHistory.removeAt(0)
        }
        fastRegime = getMajorityVote(fastRegimeHistory)

        // --- SLOW PATH: Structural invariant, slow stabilization ---
        for (i in 0..3) {
            slowSmoothedProbs[i] = (slowAlpha * updatedPosteriors[i]) + 
                                   ((1.0 - slowAlpha) * slowSmoothedProbs[i])
        }
        val slowCandidateIdx = slowSmoothedProbs.indices.maxByOrNull { slowSmoothedProbs[it] } ?: STATE_RANGE
        val slowCandidate = STATE_NAMES[slowCandidateIdx]
        
        slowRegimeHistory.add(slowCandidate)
        if (slowRegimeHistory.size > slowMedianWindow) {
            slowRegimeHistory.removeAt(0)
        }
        slowRegime = getMajorityVote(slowRegimeHistory)

        // --- DECISION FUSION NODE ---
        // Dynamically compute fusion weight (w) based on Information Shock (KL Divergence).
        // During severe shocks or high uncertainty, shift weight to the Fast Path to capture structural transitions instantly.
        // During stable periods, lean heavily on the Slow Path to prevent state-transition whipsaws.
        val normalizedShockRatio = if (infoShockThreshold > 0.0) {
            (klDivergence / infoShockThreshold).coerceIn(0.0, 2.0)
        } else {
            1.0
        }
        
        dynamicFusionWeight = if (isInformationShockDetected) {
            // High shock: prioritises fast tracking, scaled by severity
            (0.70 + (normalizedShockRatio * 0.15)).coerceIn(0.70, 0.95)
        } else {
            // Low shock: relies on long-term structural path, scaled down
            (0.10 + (normalizedShockRatio * 0.10)).coerceIn(0.05, 0.30)
        }

        // Fuse the probability vectors from both paths
        val fusedProbs = DoubleArray(4)
        for (i in 0..3) {
            fusedProbs[i] = (dynamicFusionWeight * fastSmoothedProbs[i]) + 
                            ((1.0 - dynamicFusionWeight) * slowSmoothedProbs[i])
        }
        
        // Sync the primary smoothed probability vector for backwards compatibility/dashboard display
        exponentiallySmoothedProbs = fusedProbs.clone()

        // Extract the fused regime from the fused probability densities
        val fusedCandidateIdx = fusedProbs.indices.maxByOrNull { fusedProbs[it] } ?: STATE_RANGE
        val candidateRegime = STATE_NAMES[fusedCandidateIdx]

        // Keep standard regimeDecisionHistory for backwards compatibility
        regimeDecisionHistory.add(candidateRegime)
        if (regimeDecisionHistory.size > medianWindowSize) {
            regimeDecisionHistory.removeAt(0)
        }
        val finalRegime = getMajorityVote(regimeDecisionHistory)

        Log.d(TAG, String.format(
            Locale.US,
            "HMM Dual-Path Process: Fast: %s | Slow: %s | Fused Candidate: %s | Final: %s | Weight (w): %.3f | KL Div: %.4f | Shock: %b",
            fastRegime, slowRegime, candidateRegime, finalRegime, dynamicFusionWeight, klDivergence, isInformationShockDetected
        ))

        return finalRegime
    }

    /**
     * Calculates GMM Probability Density Function for high dimensional feature vectors.
     */
    private fun calculateGmmLikelihood(
        state: Int,
        normReturn: Double,
        volatility: Double,
        normOfi: Double
    ): Double {
        val means = featureMeans[state]
        val stds = featureStds[state]

        // PDF of independent Gaussians (Diagonal Covariance assumption to handle correlations gracefully)
        val pReturn = gaussianPdf(normReturn, means[0], stds[0])
        val pVol = gaussianPdf(volatility, means[1], stds[1])
        val pOfi = gaussianPdf(normOfi, means[2], stds[2])

        // Return combined joint probability density
        return pReturn * pVol * pOfi
    }

    private fun gaussianPdf(x: Double, mean: Double, std: Double): Double {
        val exponent = -((x - mean) * (x - mean)) / (2.0 * std * std)
        return (1.0 / (std * sqrt(2.0 * Math.PI))) * Math.exp(exponent)
    }

    private fun getMajorityVote(history: List<String>): String {
        if (history.isEmpty()) return "RANGE"
        val frequencyMap = history.groupingBy { it }.eachCount()
        return frequencyMap.maxByOrNull { it.value }?.key ?: "RANGE"
    }

    /**
     * Returns current smoothed probability vector for dashboard visibility.
     */
    fun getSmoothedProbabilities(): Map<String, Double> {
        return STATE_NAMES.zip(exponentiallySmoothedProbs.toList()).toMap()
    }
}
