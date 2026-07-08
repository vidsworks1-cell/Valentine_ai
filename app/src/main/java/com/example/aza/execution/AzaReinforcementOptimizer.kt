package com.example.aza.execution

import java.util.Random
import kotlin.math.*

/**
 * AzaReinforcementOptimizer
 *
 * Implements advanced reinforcement learning, contextual bandits, and surrogate-based Bayesian optimization upgrades for AZA.
 * Specifically features:
 * 1. A Contextual Bandit with Policy Gradient (Actor-Critic framework)
 * 2. Multi-Objective Reward Formulation balancing semantic sentiment scoring (fast loop) and market performance (slow loop)
 * 3. Volatility-Adaptive Trust Region Clipping (VATRC)
 * 4. Dynamic Entropy Regularization for Exploration-Exploitation trade-offs
 * 5. SMBO / TPE Hyperparameter Tuning with Bounded-Memory / Dynamic Histogram KDE
 * 6. Random Fourier Features (RFF)-based Bayesian Linear Regression with Marginal Likelihood Tuning
 */
class AzaReinforcementOptimizer {

    private val random = Random()

    // ==========================================
    // 1 & 4. Contextual Bandit & Policy Gradient with Dynamic Entropy
    // ==========================================
    class PolicyGradientBandit(
        val numActions: Int,
        val contextDim: Int,
        var learningRate: Double = 0.05,
        var baseEntropyCoef: Double = 0.10
    ) {
        // Theta parameters: [numActions][contextDim + 1] (including bias)
        val theta = Array(numActions) { DoubleArray(contextDim + 1) { 0.0 } }
        private val random = Random()

        /**
         * Computes action probabilities for a given context using Softmax over linear scores.
         */
        fun getActionProbabilities(context: DoubleArray): DoubleArray {
            val scores = DoubleArray(numActions)
            var maxScore = -Double.MAX_VALUE
            for (a in 0 until numActions) {
                var score = theta[a][contextDim] // bias
                for (i in 0 until contextDim) {
                    score += theta[a][i] * context[i]
                }
                scores[a] = score
                if (score > maxScore) maxScore = score
            }

            // Exponentiate and normalize (log-sum-exp trick for numerical stability)
            val probs = DoubleArray(numActions)
            var sum = 0.0
            for (a in 0 until numActions) {
                probs[a] = exp(scores[a] - maxScore)
                sum += probs[a]
            }
            for (a in 0 until numActions) {
                probs[a] /= sum
            }
            return probs
        }

        /**
         * Selects an action based on context-specific policy probabilities.
         */
        fun sampleAction(context: DoubleArray): Int {
            val probs = getActionProbabilities(context)
            val r = random.nextDouble()
            var cumulative = 0.0
            for (a in 0 until numActions) {
                cumulative += probs[a]
                if (r <= cumulative) return a
            }
            return numActions - 1
        }

        /**
         * Calculates Shannon entropy of action distribution.
         */
        fun calculateEntropy(probs: DoubleArray): Double {
            var entropy = 0.0
            for (p in probs) {
                if (p > 1e-15) {
                    entropy -= p * ln(p)
                }
            }
            return entropy
        }

        /**
         * Performs a Policy Gradient step with L1/L2 Regularization and Dynamic Entropy.
         * Incorporates Volatility-Adaptive Trust Region Clipping (VATRC) directly.
         */
        fun updatePolicy(
            context: DoubleArray,
            action: Int,
            advantage: Double,
            volatility: Double,
            oldProbs: DoubleArray,
            l1Penalty: Double = 0.001,
            l2Penalty: Double = 0.005
        ) {
            val currentProbs = getActionProbabilities(context)
            
            // 2. Volatility-Adaptive Trust Region Clipping Range (VATRC)
            // Shrink trust region under high volatility to stabilize learning
            val minVol = 0.005
            val maxVol = 0.06
            val minClip = 0.05
            val maxClip = 0.25
            val normalizedVol = ((volatility - minVol) / (maxVol - minVol)).coerceIn(0.0, 1.0)
            val epsilon = maxClip - (normalizedVol * (maxClip - minClip))

            // Dynamic Entropy regularizer: higher under low volatility to encourage exploration, lower during panic
            val dynamicEntropyCoef = baseEntropyCoef * (1.0 - normalizedVol * 0.7)

            // Ratio of new policy to old policy
            val ratio = currentProbs[action] / max(1e-15, oldProbs[action])

            // Policy gradient calculation with surrogate clipping
            val isClipped = ratio < (1.0 - epsilon) || ratio > (1.0 + epsilon)
            val policyGrad = if (isClipped && (ratio * advantage < clip(ratio, 1.0 - epsilon, 1.0 + epsilon) * advantage)) {
                0.0 // Clipped gradient path
            } else {
                advantage / max(1e-15, currentProbs[action])
            }

            // Update theta weights
            for (a in 0 until numActions) {
                val isChosen = (a == action)
                val indicator = if (isChosen) 1.0 else 0.0
                
                // Base PG gradient: grad_theta log pi(a|x) = x * (indicator - pi(a|x))
                val baseGrad = (indicator - currentProbs[a]) * policyGrad

                // Entropy gradient term: grad_H = -x * pi(a|x) * (ln(pi(a|x)) - H)
                val currentEntropy = calculateEntropy(currentProbs)
                val entropyGradTerm = -currentProbs[a] * (ln(max(1e-15, currentProbs[a])) - currentEntropy)

                for (i in 0 until contextDim + 1) {
                    val featureVal = if (i < contextDim) context[i] else 1.0 // 1.0 for bias
                    
                    var gradient = baseGrad * featureVal + dynamicEntropyCoef * entropyGradTerm * featureVal
                    
                    // Apply regularizers to prevent overfitting of keyword/context weights
                    val weight = theta[a][i]
                    // L1 (Lasso) penalty gradient
                    val l1Grad = l1Penalty * sign(weight)
                    // L2 (Ridge) penalty gradient
                    val l2Grad = l2Penalty * weight

                    gradient -= (l1Grad + l2Grad)

                    // Coordinate-wise Trust Region Clamping: scale step by epsilon and clamp to respect strict trust region bounds
                    val rawUpdate = learningRate * gradient
                    val maxAllowedParamUpdate = 0.5 * epsilon // Proportional to adaptive trust-region width
                    val clampedUpdate = rawUpdate.coerceIn(-maxAllowedParamUpdate, maxAllowedParamUpdate)

                    // Update weight
                    theta[a][i] += clampedUpdate
                }
            }
        }

        private fun clip(value: Double, minVal: Double, maxVal: Double): Double {
            return value.coerceIn(minVal, maxVal)
        }
    }

    // ==========================================
    // 2. Multi-Objective Reward & Semantic Sentiment Scoring
    // ==========================================
    class MultiObjectiveRewardEvaluator {
        var sentimentWeight = 0.5
        var profitWeight = 0.5

        /**
         * Dynamically adjusts weights for the composite reward function (Dual-Loop Adaptation).
         * - Fast-Loop: Responds to negative sentiment or frustration immediately by elevating sentiment weight.
         * - Slow-Loop: Gradually shifts weights based on macro-performance (profit factor or market outcomes).
         */
        fun adaptRewardWeights(
            userSentimentScore: Double, // -1.0 (frustrated) to +1.0 (delighted)
            recentProfitFactor: Double, // macro profit ratio
            marketVolatility: Double
        ) {
            // Fast loop: immediate response to frustration
            if (userSentimentScore < -0.3) {
                sentimentWeight = (sentimentWeight + 0.15).coerceIn(0.3, 0.9)
                profitWeight = 1.0 - sentimentWeight
            } else if (userSentimentScore > 0.6) {
                // If extremely satisfied, allow slow loop trading maximization
                sentimentWeight = (sentimentWeight - 0.05).coerceIn(0.1, 0.7)
                profitWeight = 1.0 - sentimentWeight
            }

            // Slow loop: adjust based on trading performance
            if (recentProfitFactor < 1.05) {
                // Trading is underperforming or losing money; elevate profit awareness
                profitWeight = (profitWeight + 0.08).coerceIn(0.2, 0.8)
                sentimentWeight = 1.0 - profitWeight
            } else if (recentProfitFactor > 2.0 && userSentimentScore >= 0.0) {
                // High performance, user is okay; lock stable baseline
                profitWeight = (profitWeight + 0.02).coerceIn(0.2, 0.8)
                sentimentWeight = 1.0 - profitWeight
            }
        }

        /**
         * Calculates composite multi-objective reward.
         */
        fun evaluateReward(
            userSentimentScore: Double, // -1.0 to +1.0
            profitPerformance: Double // normalized profit/loss outcome, e.g. -1.0 to 1.0
        ): Double {
            // Translate semantic scores gracefully
            val sentimentComponent = userSentimentScore // range [-1, 1]
            val profitComponent = profitPerformance.coerceIn(-1.0, 1.0)
            return sentimentWeight * sentimentComponent + profitWeight * profitComponent
        }
    }

    // ==========================================
    // 4. Actor-Critic Architecture
    // ==========================================
    class ActorCriticOptimizer(
        val bandit: PolicyGradientBandit,
        val contextDim: Int,
        var criticLearningRate: Double = 0.05
    ) {
        // Critic weights (linear value function baseline)
        val criticWeights = DoubleArray(contextDim + 1) { 0.0 }

        /**
         * Evaluates state value V(s) predicted by the Critic.
         */
        fun predictValue(context: DoubleArray): Double {
            var value = criticWeights[contextDim] // bias
            for (i in 0 until contextDim) {
                value += criticWeights[i] * context[i]
            }
            return value
        }

        /**
         * Evaluates action, computes Advantage, and updates both Actor (Policy) and Critic.
         */
        fun step(
            context: DoubleArray,
            action: Int,
            sentimentScore: Double,
            profitPerformance: Double,
            volatility: Double,
            oldProbs: DoubleArray,
            rewardEvaluator: MultiObjectiveRewardEvaluator
        ): Double {
            // Predict current state value
            val stateValue = predictValue(context)

            // Compute actual composite reward
            val reward = rewardEvaluator.evaluateReward(sentimentScore, profitPerformance)

            // Advantage: A(s, a) = Reward - V(s) (simple 1-step temporal baseline advantage)
            val advantage = reward - stateValue

            // Update Critic weights (minimize MSE using gradient descent)
            // V_new = V_old + lr * advantage * context
            for (i in 0 until contextDim + 1) {
                val x_i = if (i < contextDim) context[i] else 1.0
                criticWeights[i] += criticLearningRate * advantage * x_i
            }

            // Update Actor Policy parameters
            bandit.updatePolicy(
                context = context,
                action = action,
                advantage = advantage,
                volatility = volatility,
                oldProbs = oldProbs
            )

            return advantage
        }
    }

    // ==========================================
    // 5. SMBO / TPE Hyperparameter Tuning with Bounded Memory & Dynamic Histograms
    // ==========================================
    class TpeOptimizer(val maxMemorySize: Int = 50) {
        data class Trial(val hyperparameter: Double, val loss: Double)
        val history = mutableListOf<Trial>()

        /**
         * Commits a new hyperparameter evaluation to bounded memory history.
         */
        fun recordTrial(hyperparameter: Double, loss: Double) {
            if (history.size >= maxMemorySize) {
                // Keep bounded-memory: remove oldest trial to respect on-device constraints
                history.removeAt(0)
            }
            history.add(Trial(hyperparameter, loss))
        }

        /**
         * Performs Tree-structured Parzen Estimation using Dynamic Histograms.
         * Splits historical observations into 'good' (top quantile) and 'bad' distributions,
         * models them using dynamic histograms, and selects the hyperparameter maximizing Expected Improvement.
         */
        fun suggestHyperparameter(rangeMin: Double, rangeMax: Double, numCandidates: Int = 15): Double {
            if (history.size < 10) {
                // Cold-start: return random uniform candidate
                return rangeMin + (rangeMax - rangeMin) * Random().nextDouble()
            }

            // Sort trials by loss
            val sortedTrials = history.sortedBy { it.loss }
            val splitIdx = (sortedTrials.size * 0.20).toInt().coerceAtLeast(2) // Top 20% represent good trials
            
            val goodGroup = sortedTrials.subList(0, splitIdx)
            val badGroup = sortedTrials.subList(splitIdx, sortedTrials.size)

            // Dynamic Histogram Bins
            val numBins = 10
            val goodHistogram = DoubleArray(numBins)
            val badHistogram = DoubleArray(numBins)

            fun populateHistogram(trials: List<Trial>, hist: DoubleArray) {
                for (trial in trials) {
                    val normVal = ((trial.hyperparameter - rangeMin) / (rangeMax - rangeMin)).coerceIn(0.0, 0.9999)
                    val binIdx = (normVal * numBins).toInt()
                    hist[binIdx] += 1.0
                }
                // Apply Laplace smoothing to avoid zero probability division
                for (i in 0 until numBins) {
                    hist[i] = (hist[i] + 1.0) / (trials.size + numBins)
                }
            }

            populateHistogram(goodGroup, goodHistogram)
            populateHistogram(badGroup, badHistogram)

            // Select best candidate maximizing expected improvement (ratio of good probability to bad probability)
            var bestCandidate = rangeMin
            var maxRatio = -Double.MAX_VALUE

            val rand = Random()
            for (c in 0 until numCandidates) {
                val candidate = rangeMin + (rangeMax - rangeMin) * rand.nextDouble()
                val normVal = ((candidate - rangeMin) / (rangeMax - rangeMin)).coerceIn(0.0, 0.9999)
                val binIdx = (normVal * numBins).toInt()

                val pGood = goodHistogram[binIdx]
                val pBad = badHistogram[binIdx]
                
                val ratio = pGood / max(1e-15, pBad)
                if (ratio > maxRatio) {
                    maxRatio = ratio
                    bestCandidate = candidate
                }
            }

            return bestCandidate
        }
    }

    // ==========================================
    // 6. RFF-Based Bayesian Linear Regression with Marginal Likelihood Tuning
    // ==========================================
    class RffBayesianRegressor(
        val inputDim: Int,
        var lengthScale: Double = 1.0,
        val priorVariance: Double = 1.0,
        val noiseVariance: Double = 0.05
    ) {
        // Random Fourier Feature Parameters
        private var numFeatures = 20
        private var projectionMatrix = Array(numFeatures) { DoubleArray(inputDim) }
        private var offsetVector = DoubleArray(numFeatures)
        private val random = Random()

        init {
            generateRandomFeatures(numFeatures)
        }

        /**
         * Re-generates Random Fourier Feature weights for approximation of RBF kernel.
         */
        private fun generateRandomFeatures(d: Int) {
            numFeatures = d
            projectionMatrix = Array(numFeatures) { DoubleArray(inputDim) }
            offsetVector = DoubleArray(numFeatures)
            
            // Draw weights from normal distribution N(0, 1 / l^2)
            for (i in 0 until numFeatures) {
                for (j in 0 until inputDim) {
                    projectionMatrix[i][j] = nextGaussian() / lengthScale
                }
                offsetVector[i] = random.nextDouble() * 2.0 * PI
            }
        }

        private fun nextGaussian(): Double {
            var u1: Double
            var u2: Double
            var s: Double
            do {
                u1 = 2.0 * random.nextDouble() - 1.0
                u2 = 2.0 * random.nextDouble() - 1.0
                s = u1 * u1 + u2 * u2
            } while (s >= 1.0 || s == 0.0)
            return u1 * sqrt(-2.0 * ln(s) / s)
        }

        /**
         * Maps raw context vectors to Random Fourier Features: z(x) = sqrt(2 / D) * cos(W x + b)
         */
        fun mapToRff(x: DoubleArray): DoubleArray {
            val z = DoubleArray(numFeatures)
            val scale = sqrt(2.0 / numFeatures)
            for (i in 0 until numFeatures) {
                var projection = 0.0
                for (j in 0 until inputDim) {
                    projection += projectionMatrix[i][j] * x[j]
                }
                z[i] = scale * cos(projection + offsetVector[i])
            }
            return z
        }

        /**
         * Performs adaptive feature size selection using Marginal Log-Likelihood Tuning or empirical validation.
         * Dynamically swaps feature count to preserve memory and efficiency on-device.
         */
        fun tuneRandomFeatureCount(
            inputs: List<DoubleArray>,
            targets: DoubleArray,
            candidateFeatureSizes: List<Int> = listOf(10, 20, 40)
        ) {
            if (inputs.isEmpty() || targets.isEmpty()) return
            
            var bestSize = numFeatures
            var maxMarginalLogLikelihood = -Double.MAX_VALUE

            for (size in candidateFeatureSizes) {
                // Generate a temporary candidate projection set
                val tempProj = Array(size) { DoubleArray(inputDim) }
                val tempOffset = DoubleArray(size)
                for (i in 0 until size) {
                    for (j in 0 until inputDim) {
                        tempProj[i][j] = nextGaussian() / lengthScale
                    }
                    tempOffset[i] = random.nextDouble() * 2.0 * PI
                }

                // Map inputs to RFF matrix Z [N x Size]
                val n = inputs.size
                val scale = sqrt(2.0 / size)
                val zMatrix = Array(n) { DoubleArray(size) }
                for (idx in 0 until n) {
                    val x = inputs[idx]
                    for (i in 0 until size) {
                        var proj = 0.0
                        for (j in 0 until inputDim) {
                            proj += tempProj[i][j] * x[j]
                        }
                        zMatrix[idx][i] = scale * cos(proj + tempOffset[i])
                    }
                }

                // Compute approximate marginal likelihood or empirical validation score
                // Score = -MSE - 0.02 * size (regularized complexity penalty)
                val weights = solveRidgeRegression(zMatrix, targets, 0.01)
                var mse = 0.0
                for (idx in 0 until n) {
                    var prediction = 0.0
                    for (i in 0 until size) {
                        prediction += zMatrix[idx][i] * weights[i]
                    }
                    val diff = targets[idx] - prediction
                    mse += diff * diff
                }
                mse /= n

                val score = -mse - 0.005 * size // BIC/AIC style penalty for complexity

                if (score > maxMarginalLogLikelihood) {
                    maxMarginalLogLikelihood = score
                    bestSize = size
                }
            }

            // Apply selected feature configuration
            if (bestSize != numFeatures) {
                generateRandomFeatures(bestSize)
            }
        }

        /**
         * Solves Ridge regression weights: (Z^T Z + alpha I)^(-1) Z^T y
         */
        private fun solveRidgeRegression(zMatrix: Array<DoubleArray>, targets: DoubleArray, alpha: Double): DoubleArray {
            val n = zMatrix.size
            val d = if (n > 0) zMatrix[0].size else 0
            
            // Compute Z^T * Z [D x D]
            val ztZ = Array(d) { DoubleArray(d) }
            for (i in 0 until d) {
                for (j in 0 until d) {
                    var sum = 0.0
                    for (k in 0 until n) {
                        sum += zMatrix[k][i] * zMatrix[k][j]
                    }
                    ztZ[i][j] = sum + (if (i == j) alpha else 0.0)
                }
            }

            // Compute Z^T * y [D]
            val ztY = DoubleArray(d)
            for (i in 0 until d) {
                var sum = 0.0
                for (k in 0 until n) {
                    sum += zMatrix[k][i] * targets[k]
                }
                ztY[i] = sum
            }

            // Solve system of equations using Gaussian Elimination
            return solveLinearSystem(ztZ, ztY)
        }

        private fun solveLinearSystem(matrix: Array<DoubleArray>, rightHandSide: DoubleArray): DoubleArray {
            val n = rightHandSide.size
            val a = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) matrix[i][j] else rightHandSide[i] } }

            for (p in 0 until n) {
                var maxRow = p
                for (i in p + 1 until n) {
                    if (abs(a[i][p]) > abs(a[maxRow][p])) {
                        maxRow = i
                    }
                }
                val temp = a[p]
                a[p] = a[maxRow]
                a[maxRow] = temp

                if (abs(a[p][p]) < 1e-12) {
                    // Singular matrix, return safe fallback values
                    return DoubleArray(n) { 0.0 }
                }

                for (i in p + 1 until n) {
                    val alpha = a[i][p] / a[p][p]
                    for (j in p until n + 1) {
                        a[i][j] -= alpha * a[p][j]
                    }
                }
            }

            val x = DoubleArray(n)
            for (i in n - 1 downTo 0) {
                var sum = 0.0
                for (j in i + 1 until n) {
                    sum += a[i][j] * x[j]
                }
                x[i] = (a[i][n] - sum) / a[i][i]
            }
            return x
        }
    }
}
