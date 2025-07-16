package com.example.terravue.services

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Optimized AI Service with rate limiting and quota management
 * Integrates with Google AI to provide personalized content
 */
class AIService(private val context: Context) {

    companion object {
        private const val TAG = "AIService"
        private const val MODEL_NAME = "gemini-1.5-flash"
        private const val MAX_RETRIES = 2 // Reduced retries
        private const val CACHE_EXPIRY_HOURS = 48 // Longer cache to reduce API calls
        private const val REQUEST_DELAY_MS = 4000L // 4 second delay between requests
        private const val MAX_REQUESTS_PER_SESSION = 10 // Limit per app session
    }

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = com.example.terravue.BuildConfig.GOOGLE_AI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 150 // Reduced to save quota
            }
        )
    }

    // In-memory cache for AI responses
    private val responseCache = ConcurrentHashMap<String, CachedResponse>()

    // Rate limiting and quota management
    private var lastRequestTime = 0L
    private var requestCount = 0
    private var quotaExceeded = false

    /**
     * Generate environmental comparison for CO2 emissions
     */
    suspend fun generateCO2Comparison(
        appName: String,
        co2Value: String,
        impactLevel: String
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = "co2_${appName}_$co2Value"

        // Check cache first
        getCachedResponse(cacheKey)?.let { return@withContext it }

        // Check quota status
        if (quotaExceeded || requestCount >= MAX_REQUESTS_PER_SESSION) {
            Log.d(TAG, "Quota exceeded or session limit reached, using fallback")
            return@withContext getFallbackResponse(cacheKey)
        }

        val prompt = """
            Compare $co2Value CO2 emissions from $appName app usage to everyday activities. 
            Make it relatable and easy to understand.
            Start your response with: "$appName's daily CO2 impact ($co2Value) is equivalent to"
            
            Keep response under 40 words, no markdown formatting.
        """.trimIndent()

        return@withContext generateWithRetry(prompt, cacheKey)
    }

    /**
     * Generate energy consumption comparison
     */
    suspend fun generateEnergyComparison(
        appName: String,
        energyValue: String,
        impactLevel: String
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = "energy_${appName}_$energyValue"

        getCachedResponse(cacheKey)?.let { return@withContext it }

        if (quotaExceeded || requestCount >= MAX_REQUESTS_PER_SESSION) {
            return@withContext getFallbackResponse(cacheKey)
        }

        val prompt = """
            Compare $energyValue energy consumption from $appName app usage to common household items.
            Make it practical and understandable.
            Start your response with: "$appName uses $energyValue daily, which is like"
            
            Keep response under 40 words, no markdown formatting.
        """.trimIndent()

        return@withContext generateWithRetry(prompt, cacheKey)
    }

    /**
     * Generate environmental impact explanation
     */
    suspend fun generateImpactExplanation(
        appName: String,
        impactLevel: String,
        categoryInfo: String? = null
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = "explanation_${appName}_$impactLevel"

        getCachedResponse(cacheKey)?.let { return@withContext it }

        if (quotaExceeded || requestCount >= MAX_REQUESTS_PER_SESSION) {
            return@withContext getFallbackResponse(cacheKey)
        }

        val categoryContext = categoryInfo?.let { "Category: $it. " } ?: ""

        val prompt = """
            Explain why $appName has a $impactLevel environmental impact. $categoryContext
            Focus on the main factors: data usage, server infrastructure, device processing.
            Start your response with: "$appName's $impactLevel environmental impact comes from"
            
            Keep it concise, 2 sentences max, no markdown formatting.
        """.trimIndent()

        return@withContext generateWithRetry(prompt, cacheKey)
    }

    /**
     * Generate personalized eco-friendly suggestions
     */
    suspend fun generateEcoSuggestions(
        appName: String,
        impactLevel: String,
        usageMinutes: Int? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val cacheKey = "suggestions_${appName}_${impactLevel}_$usageMinutes"

        getCachedResponse(cacheKey)?.let { cached ->
            return@withContext cached.split("|").filter { it.isNotBlank() }
        }

        if (quotaExceeded || requestCount >= MAX_REQUESTS_PER_SESSION) {
            return@withContext getFallbackSuggestions(impactLevel)
        }

        val usageContext = usageMinutes?.let { "User spends $it minutes daily. " } ?: ""

        val prompt = """
            Generate 3 practical eco-friendly tips for reducing $appName's environmental impact.
            $usageContext Impact level: $impactLevel.
            
            Format as: tip1|tip2|tip3
            Each tip should be actionable and specific to mobile app usage.
            No markdown, keep each tip under 12 words.
        """.trimIndent()

        val response = generateWithRetry(prompt, cacheKey)
        return@withContext response.split("|").filter { it.isNotBlank() }
    }

    /**
     * Generate annual impact projection with comparisons
     */
    suspend fun generateAnnualProjection(
        appName: String,
        dailyCO2: Double,
        dailyEnergy: Double
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = "annual_${appName}_${dailyCO2}_$dailyEnergy"

        getCachedResponse(cacheKey)?.let { return@withContext it }

        if (quotaExceeded || requestCount >= MAX_REQUESTS_PER_SESSION) {
            return@withContext getFallbackResponse(cacheKey)
        }

        val annualCO2 = dailyCO2 * 365
        val annualEnergy = dailyEnergy * 365

        val prompt = """
            Create an annual environmental impact summary for $appName usage.
            Annual CO2: ${String.format("%.1f", annualCO2)}g
            Annual Energy: ${String.format("%.1f", annualEnergy)}Wh
            
            Include one relatable comparison (driving distance, tree planting, etc.).
            Start with: "Using $appName for a year will generate"
            
            Keep under 50 words, no markdown formatting.
        """.trimIndent()

        return@withContext generateWithRetry(prompt, cacheKey)
    }

    /**
     * Generate content with enhanced rate limiting and error handling
     */
    private suspend fun generateWithRetry(prompt: String, cacheKey: String): String {
        // Apply rate limiting
        val timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime
        if (timeSinceLastRequest < REQUEST_DELAY_MS) {
            kotlinx.coroutines.delay(REQUEST_DELAY_MS - timeSinceLastRequest)
        }

        repeat(MAX_RETRIES) { attempt ->
            try {
                lastRequestTime = System.currentTimeMillis()
                requestCount++

                val response = generativeModel.generateContent(
                    content {
                        text(prompt)
                    }
                )

                val generatedText = response.text ?: throw Exception("Empty response")

                // Cache the successful response
                cacheResponse(cacheKey, generatedText)

                Log.d(TAG, "AI request successful. Count: $requestCount/$MAX_REQUESTS_PER_SESSION")

                return generatedText.trim()

            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                Log.w(TAG, "AI generation attempt ${attempt + 1} failed: $errorMsg")

                // Check for quota or rate limit errors
                if (errorMsg.contains("quota", ignoreCase = true) ||
                    errorMsg.contains("rate", ignoreCase = true) ||
                    errorMsg.contains("limit", ignoreCase = true)) {

                    Log.w(TAG, "Quota/rate limit detected, disabling AI for this session")
                    quotaExceeded = true
                    return getFallbackResponse(cacheKey)
                }

                if (attempt == MAX_RETRIES - 1) {
                    return getFallbackResponse(cacheKey)
                }

                // Wait before retry with exponential backoff
                kotlinx.coroutines.delay(2000L * (attempt + 1))
            }
        }

        return getFallbackResponse(cacheKey)
    }

    /**
     * Cache management with longer expiry
     */
    private fun cacheResponse(key: String, response: String) {
        responseCache[key] = CachedResponse(
            content = response,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun getCachedResponse(key: String): String? {
        val cached = responseCache[key] ?: return null

        val ageHours = (System.currentTimeMillis() - cached.timestamp) / (1000 * 60 * 60)

        return if (ageHours < CACHE_EXPIRY_HOURS) {
            cached.content
        } else {
            responseCache.remove(key)
            null
        }
    }

    /**
     * Enhanced fallback responses when AI generation fails
     */
    private fun getFallbackResponse(cacheKey: String): String {
        return when {
            cacheKey.startsWith("co2_") -> "This app's CO2 emissions contribute to your digital carbon footprint through data usage and processing"
            cacheKey.startsWith("energy_") -> "Energy consumption varies based on your usage patterns, with background data sync being a major factor"
            cacheKey.startsWith("explanation_") -> "This app's environmental impact comes from server communication, data processing, and screen usage during operation"
            cacheKey.startsWith("suggestions_") -> "Reduce usage time|Enable dark mode|Limit background sync"
            cacheKey.startsWith("annual_") -> "Using this app for a year contributes to your overall digital environmental footprint through accumulated energy consumption"
            else -> "Environmental impact information currently unavailable"
        }
    }

    /**
     * Fallback suggestions based on impact level
     */
    private fun getFallbackSuggestions(impactLevel: String): List<String> {
        return when (impactLevel.uppercase()) {
            "HIGH" -> listOf(
                "Limit daily usage time",
                "Enable dark mode to save energy",
                "Download content for offline use"
            )
            "MEDIUM" -> listOf(
                "Reduce video streaming quality",
                "Close app when not in use",
                "Use Wi-Fi instead of mobile data"
            )
            "LOW" -> listOf(
                "Great choice for eco-friendly app usage",
                "Keep using efficiently",
                "Consider similar low-impact alternatives"
            )
            else -> listOf(
                "Monitor your usage patterns",
                "Enable energy-saving features",
                "Use mindfully"
            )
        }
    }

    /**
     * Clear cache and reset session counters
     */
    fun clearCache() {
        responseCache.clear()
        requestCount = 0
        quotaExceeded = false
        Log.d(TAG, "AI response cache cleared and session reset")
    }

    /**
     * Get cache statistics with quota info
     */
    fun getCacheStats(): CacheStats {
        val now = System.currentTimeMillis()
        var validEntries = 0
        var expiredEntries = 0

        responseCache.values.forEach { cached ->
            val ageHours = (now - cached.timestamp) / (1000 * 60 * 60)
            if (ageHours < CACHE_EXPIRY_HOURS) {
                validEntries++
            } else {
                expiredEntries++
            }
        }

        return CacheStats(
            totalEntries = responseCache.size,
            validEntries = validEntries,
            expiredEntries = expiredEntries,
            requestCount = requestCount,
            quotaExceeded = quotaExceeded
        )
    }

    /**
     * Check if AI service is available
     */
    suspend fun isAIAvailable(): Boolean {
        return try {
            if (quotaExceeded) {
                Log.d(TAG, "AI marked as quota exceeded")
                return false
            }

            // Don't make actual request to check availability to save quota
            // Just check if API key is configured
            val apiKey = com.example.terravue.BuildConfig.GOOGLE_AI_API_KEY
            apiKey.isNotBlank() && apiKey != "YOUR_API_KEY_HERE"

        } catch (e: Exception) {
            Log.w(TAG, "AI availability check failed: ${e.message}")
            false
        }
    }

    /**
     * Reset quota status (call when quota should be refreshed)
     */
    fun resetQuotaStatus() {
        quotaExceeded = false
        requestCount = 0
        Log.d(TAG, "Quota status reset")
    }

    /**
     * Get current session info
     */
    fun getSessionInfo(): SessionInfo {
        return SessionInfo(
            requestCount = requestCount,
            maxRequests = MAX_REQUESTS_PER_SESSION,
            quotaExceeded = quotaExceeded,
            cacheSize = responseCache.size
        )
    }

    // Data classes
    data class CachedResponse(
        val content: String,
        val timestamp: Long
    )

    data class CacheStats(
        val totalEntries: Int,
        val validEntries: Int,
        val expiredEntries: Int,
        val requestCount: Int,
        val quotaExceeded: Boolean
    )

    data class SessionInfo(
        val requestCount: Int,
        val maxRequests: Int,
        val quotaExceeded: Boolean,
        val cacheSize: Int
    )
}