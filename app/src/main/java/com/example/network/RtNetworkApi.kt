package com.example.network

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ===============================================
// DATA STRUCTURES & CONFIGURATION FOR RT SCORE
// ===============================================

enum class Source {
    GEMINI,           // 🍅 red   official-style
    TMDB_SUBSTITUTE,  // 🎬 blue  substitute
    UNAVAILABLE       // hide row
}

data class RTResult(
    val tomatometer: Int? = null,
    val audienceScore: Int? = null,
    val status: String? = null,
    val audienceStatus: String? = null,
    val consensus: String? = null,
    val source: Source = Source.UNAVAILABLE
)

fun RTCacheEntity.toRTResult(): RTResult {
    return RTResult(
        tomatometer = tomatometer,
        audienceScore = audienceScore,
        status = status,
        consensus = consensus,
        source = try { Source.valueOf(source) } catch (e: Exception) { Source.UNAVAILABLE }
    )
}

fun RTResult.toCacheEntity(key: String): RTCacheEntity {
    return RTCacheEntity(
        key = key,
        tomatometer = tomatometer,
        audienceScore = audienceScore,
        status = status,
        consensus = consensus,
        source = source.name,
        cachedAt = System.currentTimeMillis()
    )
}

fun MovieRatingEntity.updateWithRTResult(rtResult: RTResult): MovieRatingEntity {
    val rtPercent = if (rtResult.tomatometer != null) "${rtResult.tomatometer}%" else "N/A"
    val audPercent = if (rtResult.audienceScore != null) "${rtResult.audienceScore}%" else "N/A"
    return this.copy(
        rottenTomatoes = rtPercent,
        tomatoMeter = rtPercent,
        tomatoImage = rtResult.status ?: "N/A",
        tomatoConsensus = rtResult.consensus ?: "N/A",
        tomatoUserMeter = audPercent,
        tomatoURL = rtResult.source.name,
        criticConsensus = rtResult.consensus ?: this.criticConsensus
    )
}

// ===============================================
// DATA STRUCTURES FOR PARENTS GUIDE
// ===============================================

sealed class ParentsGuideState {
    object Loading : ParentsGuideState()
    data class Success(val guide: ParentsGuide) : ParentsGuideState()
    data class Error(val message: String) : ParentsGuideState()
}

data class ParentsGuide(
    val sexNudity: GuideCategory,
    val violenceGore: GuideCategory,
    val profanity: GuideCategory,
    val substanceUse: GuideCategory,
    val frighteningScenes: GuideCategory,
    val overallRating: String,
    val certificationIndia: String,
    val minAge: Int,
    val summary: String
)

data class GuideCategory(
    val rating: String,
    val description: String
)

fun GuideCategory.color(): Int {
    return when (rating.lowercase().trim()) {
        "none"     -> 0xFF2ECC71.toInt() // green
        "mild"     -> 0xFFF1C40F.toInt() // yellow
        "moderate" -> 0xFFE67E22.toInt() // orange
        "severe"   -> 0xFFE74C3C.toInt() // red
        else       -> 0xFF8A8AB0.toInt() // grey
    }
}

fun ParentsGuideCacheEntity.toParentsGuide(): ParentsGuide {
    return ParentsGuide(
        sexNudity = GuideCategory(sexNudityRating, sexNudityDesc),
        violenceGore = GuideCategory(violenceRating, violenceDesc),
        profanity = GuideCategory(profanityRating, profanityDesc),
        substanceUse = GuideCategory(substanceRating, substanceDesc),
        frighteningScenes = GuideCategory(frighteningRating, frighteningDesc),
        overallRating = overallRating,
        certificationIndia = certificationIndia,
        minAge = minAge,
        summary = summary
    )
}

fun ParentsGuide.toCacheEntity(titleKey: String): ParentsGuideCacheEntity {
    return ParentsGuideCacheEntity(
        titleKey = titleKey,
        sexNudityRating = sexNudity.rating,
        sexNudityDesc = sexNudity.description,
        violenceRating = violenceGore.rating,
        violenceDesc = violenceGore.description,
        profanityRating = profanity.rating,
        profanityDesc = profanity.description,
        substanceRating = substanceUse.rating,
        substanceDesc = substanceUse.description,
        frighteningRating = frighteningScenes.rating,
        frighteningDesc = frighteningScenes.description,
        overallRating = overallRating,
        certificationIndia = certificationIndia,
        minAge = minAge,
        summary = summary,
        cachedAt = System.currentTimeMillis()
    )
}

// ===============================================
// COMMON GEMINI API CALLER HELPERS
// ===============================================

object RtRatingFetcher {
    private const val TAG = "RT_FETCH"

    private fun getGeminiApiKey(context: Context): String {
        return GeminiClient.customApiKey?.takeIf { it.isNotBlank() }
            ?: context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE).getString("gemini_api_key", null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GEMINI_API_KEY
    }

    private suspend fun callGeminiApi(context: Context, prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val key = getGeminiApiKey(context)
            if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
                Log.e(TAG, "Gemini API key is not configured!")
                return@withContext null
            }
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.1f
                )
            )
            val response = GeminiClient.getMoshiApi().generateContent(key, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API call failed", e)
            null
        }
    }

    // ===============================================
    // BILL 1 — ROTTEN TOMATOES ENGINE IMPLEMENTATION
    // ===============================================

    fun buildRTPrompt(title: String, year: String): String {
        return """
          What is the current Rotten Tomatoes Tomatometer score and Audience Score for "$title" ($year)?

          Return ONLY this JSON, no markdown, no explanation, start with { directly:
          {
            "tomatometer": 93,
            "audienceScore": 89,
            "status": "Certified Fresh",
            "audienceStatus": "Upright",
            "criticsConsensus": "one sentence"
          }
          If score unknown use null for that field.
        """.trimIndent()
    }

    fun parseRTJson(raw: String): RTResult {
        return try {
            val cleaned = raw
                .replace("```json", "")
                .replace("```", "")
                .trim()
            val start = cleaned.indexOf("{")
            val end = cleaned.lastIndexOf("}") + 1
            if (start == -1 || end <= 0) {
                return RTResult(source = Source.UNAVAILABLE)
            }
            val json = JSONObject(cleaned.substring(start, end))
            
            val tm = if (json.isNull("tomatometer")) -1 else json.optInt("tomatometer", -1)
            val aud = if (json.isNull("audienceScore")) -1 else json.optInt("audienceScore", -1)
            
            RTResult(
                tomatometer = tm.takeIf { it >= 0 },
                audienceScore = aud.takeIf { it >= 0 },
                status = json.optString("status").takeIf { it.isNotEmpty() && it != "null" },
                audienceStatus = json.optString("audienceStatus").takeIf { it.isNotEmpty() && it != "null" },
                consensus = json.optString("criticsConsensus").takeIf { it.isNotEmpty() && it != "null" },
                source = Source.GEMINI
            )
        } catch (e: Exception) {
            Log.e("RT_PARSE", "Failed: ${e.message}")
            RTResult(source = Source.UNAVAILABLE)
        }
    }

    suspend fun fetchRTScore(
        context: Context,
        title: String,
        year: String,
        tmdbId: Int,
        isMovie: Boolean
    ): RTResult = withContext(Dispatchers.IO) {
        Log.d("RT_FETCH", "Start: $title $year")
        val cacheKey = "${title}_${year}"
        val db = MovieDatabase.getDatabase(context)
        val rtCacheDao = db.rtCacheDao()

        // 0. Check cache first
        try {
            val cached = rtCacheDao.get(cacheKey, System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
            if (cached != null) {
                Log.d("RT_FETCH", "Cache Hit: $title ($year)")
                return@withContext cached.toRTResult()
            }
        } catch (e: Exception) {
            Log.e("RT", "Cache check failed", e)
        }

        // Attempt 1: Gemini with title + year
        try {
            val prompt = buildRTPrompt(title, year)
            val raw = callGeminiApi(context, prompt)
            Log.d("RT_FETCH", "Gemini raw: $raw")
            if (raw != null) {
                val result = parseRTJson(raw)
                Log.d("RT_FETCH", "Parsed score: $result")
                if (result.tomatometer != null) {
                    rtCacheDao.insert(result.toCacheEntity(cacheKey))
                    Log.d("RT_FETCH", "Final source: ${result.source}")
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            Log.e("RT", "Gemini failed: ${e.message}")
        }

        // Attempt 2: Gemini with more context
        try {
            val tmdbDetails = TmdbClient.fetchMovieDetails(context, tmdbId, !isMovie)
            if (tmdbDetails != null) {
                val genresStr = tmdbDetails.genres?.joinToString { it.name } ?: "Unknown"
                val prompt2 = """
                  Movie/Show details:
                  Title: $title
                  Year: $year  
                  Genre: $genresStr
                  Overview: ${tmdbDetails.overview}
                  
                  Based on this, what is the Rotten Tomatoes Tomatometer and Audience Score?
                  Return ONLY JSON no markdown:
                  {"tomatometer":93,"audienceScore":89,"status":"Fresh","audienceStatus":"Upright","criticsConsensus":"sentence here"}
                """.trimIndent()

                val raw2 = callGeminiApi(context, prompt2)
                Log.d("RT_FETCH", "Gemini attempt 2 raw: $raw2")
                if (raw2 != null) {
                    val result2 = parseRTJson(raw2)
                    Log.d("RT_FETCH", "Parsed attempt 2 score: $result2")
                    if (result2.tomatometer != null) {
                        rtCacheDao.insert(result2.toCacheEntity(cacheKey))
                        Log.d("RT_FETCH", "Final source: ${result2.source}")
                        return@withContext result2
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RT", "Gemini attempt 2 failed: $e")
        }

        // Attempt 3: TMDb score as substitute
        try {
            val details = TmdbClient.fetchMovieDetails(context, tmdbId, !isMovie)
            if (details != null && details.vote_count != null && details.vote_count > 50) {
                val voteAverage = details.vote_average ?: 0.0
                val score = (voteAverage * 10).toInt()
                val res = RTResult(
                    tomatometer = score,
                    source = Source.TMDB_SUBSTITUTE,
                    status = when {
                        score >= 75 -> "Certified Fresh"
                        score >= 60 -> "Fresh"
                        else -> "Rotten"
                    }
                )
                Log.d("RT_FETCH", "TMDb fallback score selected: $score")
                Log.d("RT_FETCH", "Final source: ${res.source}")
                return@withContext res
            }
        } catch (e: Exception) {
            Log.e("RT", "TMDb fallback failed: $e")
        }

        val finalRes = RTResult(source = Source.UNAVAILABLE)
        Log.d("RT_FETCH", "Final source: ${finalRes.source}")
        return@withContext finalRes
    }

    // ===============================================
    // BUG 2 — PARENTS GUIDE ENGINE & PARSER
    // ===============================================

    fun buildParentsGuidePrompt(
        title: String,
        year: String,
        certification: String?
    ): String {
        return """
            You are a film content rating expert.
            Provide a parents guide for "$title" ($year).
            ${if (certification != null) "Official certification: $certification" else ""}

            Rules:
            - Use ONLY these rating values exactly:
              None, Mild, Moderate, Severe
            - Keep descriptions under 10 words each
            - Return ONLY raw JSON
            - Do NOT use markdown or backticks
            - Start your response with { character
            - No text before or after the JSON

            {
              "sexNudity": {
                "rating": "None",
                "description": "No sexual content"
              },
              "violenceGore": {
                "rating": "Moderate", 
                "description": "Action violence, no gore"
              },
              "profanity": {
                "rating": "Mild",
                "description": "Occasional mild language"
              },
              "substanceUse": {
                "rating": "None",
                "description": "No drug or alcohol use"
              },
              "frighteningScenes": {
                "rating": "Severe",
                "description": "Intense scenes throughout"
              },
              "overallRating": "PG-13",
              "certificationIndia": "U/A 13+",
              "minAge": 13,
              "summary": "Suitable for teens and above"
            }

            Now provide the same JSON for "$title" ($year). Start with { directly.
        """.trimIndent()
    }

    fun parseParentsGuide(raw: String): ParentsGuide {
        return try {
            Log.d("PG_RAW", "Gemini output: $raw")
            
            val cleaned = raw
                .replace("```json", "")
                .replace("```", "")
                .replace("\n", " ")
                .replace("\r", "")
                .trim()

            val start = cleaned.indexOf("{")
            val end = cleaned.lastIndexOf("}") + 1
            
            if (start == -1 || end <= 0) {
                Log.e("PG_PARSE", "No JSON found in: $raw")
                return getDefaultParentsGuide()
            }

            val jsonStr = cleaned.substring(start, end)
            Log.d("PG_JSON", "Extracted: $jsonStr")
            
            val json = JSONObject(jsonStr)

            fun safeCategory(key: String): GuideCategory {
                return try {
                    val obj = json.getJSONObject(key)
                    GuideCategory(
                        rating = obj.optString("rating", "Unknown"),
                        description = obj.optString("description", "No data")
                    )
                } catch (e: Exception) {
                    Log.w("PG_PARSE", "$key missing: $e")
                    GuideCategory("Unknown", "No data")
                }
            }

            ParentsGuide(
                sexNudity = safeCategory("sexNudity"),
                violenceGore = safeCategory("violenceGore"),
                profanity = safeCategory("profanity"),
                substanceUse = safeCategory("substanceUse"),
                frighteningScenes = safeCategory("frighteningScenes"),
                overallRating = json.optString("overallRating", "NR"),
                certificationIndia = json.optString("certificationIndia", "?"),
                minAge = json.optInt("minAge", 0),
                summary = json.optString("summary", "No summary available")
            )
        } catch (e: Exception) {
            Log.e("PG_PARSE", "Fatal parse error: $e")
            getDefaultParentsGuide()
        }
    }

    fun getDefaultParentsGuide(): ParentsGuide {
        val unknown = GuideCategory("Unknown", "Data unavailable")
        return ParentsGuide(
            sexNudity = unknown,
            violenceGore = unknown,
            profanity = unknown,
            substanceUse = unknown,
            frighteningScenes = unknown,
            overallRating = "NR",
            certificationIndia = "?",
            minAge = 0,
            summary = "Parents guide unavailable"
        )
    }

    // VM loading logic function
    suspend fun loadParentsGuide(
        context: Context,
        title: String,
        year: String,
        certification: String?
    ): ParentsGuide = withContext(Dispatchers.IO) {
        Log.d("PG_FETCH", "Start: $title $year")
        val cacheKey = title
        val db = MovieDatabase.getDatabase(context)
        val parentsGuideCacheDao = db.parentsGuideDao()
        val expiryTime = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L // 30 days

        try {
            val cached = parentsGuideCacheDao.get(cacheKey, expiryTime)
            if (cached != null) {
                Log.d("PG_FETCH", "Cache Hit for $title")
                val guide = cached.toParentsGuide()
                Log.d("PG_FETCH", "Parsed guide: $guide")
                return@withContext guide
            }
        } catch (e: Exception) {
            Log.e("PG_FETCH", "Cache fetch error", e)
        }

        try {
            val prompt = buildParentsGuidePrompt(title, year, certification)
            val raw = callGeminiApi(context, prompt)
            Log.d("PG_FETCH", "Gemini raw output: $raw")
            if (raw != null) {
                val guide = parseParentsGuide(raw)
                try {
                    parentsGuideCacheDao.insert(guide.toCacheEntity(cacheKey))
                    Log.d("PG_FETCH", "Cached parents guide successfully")
                } catch (e: Exception) {
                    Log.e("PG_FETCH", "Cache save failed", e)
                }
                Log.d("PG_FETCH", "Parsed guide: $guide")
                return@withContext guide
            }
        } catch (e: Exception) {
            Log.e("PG_FETCH", "Gemini fetch parents guide error", e)
        }

        val defaultGuide = getDefaultParentsGuide()
        Log.d("PG_FETCH", "Parsed guide: $defaultGuide")
        return@withContext defaultGuide
    }
}
