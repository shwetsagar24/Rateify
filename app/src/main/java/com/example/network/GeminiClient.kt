package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // In-memory or sharedPreference-backed custom user-supplied API key
    var customApiKey: String? = null

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApi::class.java)
    }

    data class CandidateWithScore(
        val candidate: OmdbSearchResult,
        val score: Int
    )

    fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("""['"’“”]"""), "") // remove quotes entirely
            .replace(Regex("""[^a-z0-9\s]"""), " ") // replace other punctuation with space
            .replace(Regex("""\s+"""), " ") // normalize spacing
            .trim()
    }

    fun isTitleMatch(expected: String, fetched: String): Boolean {
        val normExpected = normalizeTitle(expected)
        val normFetched = normalizeTitle(fetched)
        val result = normExpected == normFetched
        Log.d(TAG, "[MOVIE MATCH AUDIT] Title Match Check: Expected=\"$expected\" (Norm=\"$normExpected\"), Fetched=\"$fetched\" (Norm=\"$normFetched\"), Match=$result")
        return result
    }

    fun isYearMatch(expected: String, fetched: String): Boolean {
        val expectedYears = Regex("""\d{4}""").findAll(expected).map { it.value }.toList()
        val fetchedYears = Regex("""\d{4}""").findAll(fetched).map { it.value }.toList()
        if (expectedYears.isEmpty() || fetchedYears.isEmpty()) {
            Log.d(TAG, "[MOVIE MATCH AUDIT] Year Match Check: Missing year, default Match=true (Expected=\"$expected\", Fetched=\"$fetched\")")
            return true
        }
        val result = expectedYears.any { ey -> fetchedYears.any { fy -> ey == fy } }
        Log.d(TAG, "[MOVIE MATCH AUDIT] Year Match Check: Expected=\"$expected\", Fetched=\"$fetched\", ExpectedYears=$expectedYears, FetchedYears=$fetchedYears, Match=$result")
        return result
    }

    fun isExactMatch(query: String, candidate: OmdbSearchResult): Boolean {
        val normQuery = normalizeTitle(query)
        val normCandTitle = normalizeTitle(candidate.Title ?: "")
        
        // 1. normalized(query) == normalized(candidate)
        if (normQuery == normCandTitle) {
            Log.d(TAG, "[MOVIE MATCH AUDIT] isExactMatch: TRUE (normalized match of query and candidate title: \"$normQuery\")")
            return true
        }
        
        // 2. normalized(query) == normalized(candidate title + year)
        val candYear = candidate.Year ?: ""
        val normCandTitleWithYear = normalizeTitle("${candidate.Title ?: ""} $candYear")
        if (normQuery == normCandTitleWithYear) {
            Log.d(TAG, "[MOVIE MATCH AUDIT] isExactMatch: TRUE (normalized match of query and candidate title with year: \"$normQuery\")")
            return true
        }
        
        // Fallback exact check with first year in the candidate's year (e.g. "2008–2013" -> "2008")
        val cleanYear = candYear.split("-").firstOrNull()?.replace(Regex("""[^0-9]"""), "")?.trim() ?: ""
        if (cleanYear.isNotEmpty()) {
            val normCandTitleWithSingleYear = normalizeTitle("${candidate.Title ?: ""} $cleanYear")
            if (normQuery == normCandTitleWithSingleYear) {
                Log.d(TAG, "[MOVIE MATCH AUDIT] isExactMatch: TRUE (normalized match of query and candidate title with clean single year: \"$normQuery\")")
                return true
            }
        }
        
        // Fallback: If query contains year (e.g. "Breaking Bad (2008)") vs candidate "Breaking Bad" (2008-2013)
        var cleanTitle = query.trim()
        val yearParenMatch = Regex("""\s*\(\s*(\d{4})\s*\)\s*$""").find(cleanTitle)
        var yearParam: String? = null
        if (yearParenMatch != null) {
            yearParam = yearParenMatch.groupValues[1]
            cleanTitle = cleanTitle.replace(Regex("""\s*\(\s*\d{4}\s*\)\s*$"""), "").trim()
        } else {
            val yearEndMatch = Regex("""\s+(\d{4})$""").find(cleanTitle)
            if (yearEndMatch != null) {
                yearParam = yearEndMatch.groupValues[1]
                cleanTitle = cleanTitle.replace(Regex("""\s+\d{4}$"""), "").trim()
            }
        }
        
        val normCleanTitle = normalizeTitle(cleanTitle)
        if (normCleanTitle == normCandTitle) {
            if (yearParam != null) {
                if (candYear.contains(yearParam)) {
                    Log.d(TAG, "[MOVIE MATCH AUDIT] isExactMatch: TRUE (clean title matches candidate title, and specified year is correct: \"$normCleanTitle\", $yearParam)")
                    return true
                }
            } else {
                Log.d(TAG, "[MOVIE MATCH AUDIT] isExactMatch: TRUE (clean title matches candidate title: \"$normCleanTitle\")")
                return true
            }
        }
        
        Log.d(TAG, "[MOVIE MATCH AUDIT] isExactMatch: FALSE for query: \"$query\", candidate: \"${candidate.Title}\"")
        return false
    }

    fun calculateConfidenceScore(
        queryTitle: String,
        queryYear: String?,
        item: OmdbSearchResult
    ): Int {
        val qNorm = normalizeTitle(queryTitle)
        val iTitle = item.Title ?: ""
        val iNorm = normalizeTitle(iTitle)
        
        if (qNorm.isEmpty() || iNorm.isEmpty()) return 0
        
        var score = 0
        
        // Exact match of normalized titles is heavily preferred
        val exactMatchText = (qNorm == iNorm)
        if (exactMatchText) {
            score += 85
        } else {
            // It merely contains or starts with - give it a much lower score (merely contains never exact match)
            if (iNorm.startsWith(qNorm) || iNorm.endsWith(qNorm)) {
                val ratio = qNorm.length.toDouble() / iNorm.length.toDouble()
                score += (30 * ratio).toInt()
            } else if (iNorm.contains(qNorm)) {
                val ratio = qNorm.length.toDouble() / iNorm.length.toDouble()
                score += (15 * ratio).toInt()
            } else {
                return 0
            }
        }
        
        // Exact case-insensitive and punctuation-included match bonus
        if (queryTitle.trim().lowercase() == iTitle.trim().lowercase()) {
            score += 5
        }
        
        // Year match
        val itemYear = item.Year ?: ""
        if (queryYear != null) {
            if (itemYear.contains(queryYear)) {
                score += 10
            } else {
                score -= 30
            }
        }
        
        // Content Type preference (Movie, Series) - TV Series & Movie strongly preferred
        val iType = item.Type?.lowercase() ?: ""
        when (iType) {
            "series" -> score += 10
            "movie" -> score += 10
            "episode" -> score -= 20
            else -> score -= 30
        }
        
        // Strong Penalties for Non-Official metadata / Documentary / Behind the Scenes / Specials
        val lowerTitle = iTitle.lowercase()
        val penalties = listOf(
            "behind the scenes" to 50,
            "making of" to 50,
            "creating" to 45,
            "featurette" to 50,
            "documentary" to 45,
            "extra" to 40,
            "interview" to 50,
            "bonus" to 50,
            "retrospective" to 50,
            "promo" to 40,
            "trailer" to 50,
            "special" to 35
        )
        
        for ((badWord, penalty) in penalties) {
            if (lowerTitle.contains(badWord)) {
                if (!qNorm.contains(badWord)) {
                    score -= penalty
                }
            }
        }
        
        return score.coerceIn(0, 100)
    }

    suspend fun getRankedCandidates(query: String): List<CandidateWithScore> = withContext(Dispatchers.IO) {
        var cleanTitle = query.trim()
        var yearParam: String? = null
        
        // Extract trailing year in parentheses like "(2012)"
        val yearParenMatch = Regex("""\s*\(\s*(\d{4})\s*\)\s*$""").find(cleanTitle)
        if (yearParenMatch != null) {
            yearParam = yearParenMatch.groupValues[1]
            cleanTitle = cleanTitle.replace(Regex("""\s*\(\s*\d{4}\s*\)\s*$"""), "").trim()
        } else {
            // Check if trailing digits looks like a year, e.g. "Inception 2010"
            val yearEndMatch = Regex("""\s+(\d{4})$""").find(cleanTitle)
            if (yearEndMatch != null) {
                yearParam = yearEndMatch.groupValues[1]
                cleanTitle = cleanTitle.replace(Regex("""\s+\d{4}$"""), "").trim()
            }
        }

        // Remove common streaming platform specific appendants to get cleaner searches
        cleanTitle = cleanTitle.replace(Regex("""(?i)\s+(hd|uhd|sd|4k|5.1|hdr|atmos|hindi|dual audio|english|multi)$"""), "").trim()

        val encodedClean = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
        
        val searchUrl = "https://www.omdbapi.com/?apikey=8171c492&s=$encodedClean"
        
        Log.d(TAG, "[MOVIE MATCH AUDIT] User search query: \"$query\" (Cleaned: \"$cleanTitle\", Year: ${yearParam ?: "N/A"})")
        Log.d(TAG, "Search URL for ranking: $searchUrl")
        
        val searchResponseStr = makeHttpRequest(searchUrl)
        val results = mutableListOf<CandidateWithScore>()
        if (searchResponseStr != null) {
            try {
                val searchAdapter = moshi.adapter(OmdbSearchResponse::class.java)
                val searchResponse = searchAdapter.fromJson(searchResponseStr)
                if (searchResponse != null && searchResponse.Response == "True" && !searchResponse.Search.isNullOrEmpty()) {
                    searchResponse.Search.forEach { item ->
                        val score = calculateConfidenceScore(cleanTitle, yearParam, item)
                        results.add(CandidateWithScore(item, score))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse OMDb search list response.", e)
            }
        }
        
        // Sort descending
        results.sortByDescending { it.score }
        
        results.forEach { entry ->
            Log.d(TAG, "[MOVIE MATCH AUDIT] Candidate: \"${entry.candidate.Title}\" (${entry.candidate.Year}) [type: ${entry.candidate.Type}] ID=${entry.candidate.imdbID} -> Score=${entry.score}")
        }
        
        results
    }

    private suspend fun makeHttpRequest(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP execution error", e)
            null
        }
    }

    suspend fun fetchMovieReviews(query: String): MovieRatingResult? {
        Log.d(TAG, "[MOVIE MATCH AUDIT] fetchMovieReviews called with query: $query")
        val candidates = getRankedCandidates(query)
        if (candidates.isEmpty()) {
            val cleanTitle = query.trim()
            val encodedClean = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
            val directUrl = "https://www.omdbapi.com/?apikey=8171c492&t=$encodedClean&plot=full&tomatoes=true"
            Log.d(TAG, "[MOVIE MATCH AUDIT] Fallback direct title query: $directUrl")
            val responseStr = makeHttpRequest(directUrl)
            if (responseStr != null) {
                val parsed = moshi.adapter(OmdbResponse::class.java).fromJson(responseStr)
                if (parsed != null && parsed.Response == "True" && !parsed.imdbID.isNullOrBlank()) {
                    Log.d(TAG, "[MOVIE MATCH AUDIT] Fallback direct title successfully found ID: ${parsed.imdbID}")
                    return fetchMovieReviewsByImdbId(parsed.imdbID, cleanTitle, parsed.Year)
                }
            }
            Log.e(TAG, "[MOVIE MATCH AUDIT] No match found for: $query. Returning null.")
            return null
        }
        
        // Iterate ranked candidates from highest score downward and find the first one that passes validation
        for (candidateWithScore in candidates) {
            val candidate = candidateWithScore.candidate
            val candidateId = candidate.imdbID ?: ""
            Log.d(TAG, "[MOVIE MATCH AUDIT] Verifying ranked candidate: Title=\"${candidate.Title}\", Year=\"${candidate.Year}\", ID=\"$candidateId\", Score=${candidateWithScore.score}")
            val res = fetchMovieReviewsByImdbId(candidateId, candidate.Title, candidate.Year)
            if (res != null) {
                Log.d(TAG, "[MOVIE MATCH AUDIT] Accepted candidate: Title=\"${candidate.Title}\" after successful verification.")
                return res
            } else {
                Log.w(TAG, "[MOVIE MATCH AUDIT] Candidate \"${candidate.Title}\" failed verification. Trying next candidate.")
            }
        }
        
        Log.e(TAG, "[MOVIE MATCH AUDIT] All candidates failed verification for query: $query. Returning null.")
        return null
    }

    suspend fun fetchMovieReviewsByImdbId(
        imdbID: String,
        expectedTitle: String? = null,
        expectedYear: String? = null
    ): MovieRatingResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "[MOVIE MATCH AUDIT] fetchMovieReviewsByImdbId called for unique identifier: $imdbID")
        
        val queryIdUrl = "https://www.omdbapi.com/?apikey=8171c492&i=$imdbID&plot=full&tomatoes=true"
        val detailResponseStr = makeHttpRequest(queryIdUrl)
        if (detailResponseStr == null) {
            Log.e(TAG, "Failed to fetch detailed OMDb response by ID: $imdbID")
            return@withContext null
        }
        
        val omdb = try {
            moshi.adapter(OmdbResponse::class.java).fromJson(detailResponseStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OMDb detailed response by ID: $imdbID", e)
            null
        }
        
        if (omdb == null || omdb.Response != "True") {
            Log.e(TAG, "OMDb response failed for ID: $imdbID")
            return@withContext null
        }
        
        val title = omdb.Title ?: "N/A"
        val year = omdb.Year ?: "N/A"
        val fetchedId = omdb.imdbID ?: "N/A"
        
        var validationSuccess = true
        if (expectedTitle != null && !isTitleMatch(expectedTitle, title)) {
            Log.e(TAG, "[MOVIE MATCH AUDIT] Title validation failed! Expected: \"$expectedTitle\", Returned: \"$title\". Rejecting candidate.")
            validationSuccess = false
        }
        if (expectedYear != null && !isYearMatch(expectedYear, year)) {
            Log.e(TAG, "[MOVIE MATCH AUDIT] Year validation failed! Expected: \"$expectedYear\", Returned: \"$year\". Rejecting candidate.")
            validationSuccess = false
        }
        if (imdbID != "N/A" && fetchedId != "N/A" && imdbID.lowercase() != fetchedId.lowercase()) {
            Log.e(TAG, "[MOVIE MATCH AUDIT] ID validation failed! Expected: \"$imdbID\", Returned: \"$fetchedId\". Rejecting candidate.")
            validationSuccess = false
        }

        // Detailed debug logs for point 11 & 14:
        Log.d(TAG, "[MOVIE MATCH AUDIT] VALIDATION DETAILS:")
        Log.d(TAG, "  - User Query/Selected Title: ${expectedTitle ?: "N/A"}")
        Log.d(TAG, "  - Selected Title: ${expectedTitle ?: "N/A"}")
        Log.d(TAG, "  - Selected Year: ${expectedYear ?: "N/A"}")
        Log.d(TAG, "  - Selected ID: $imdbID")
        Log.d(TAG, "  - Fetched Title: $title")
        Log.d(TAG, "  - Fetched Year: $year")
        Log.d(TAG, "  - Fetched ID: $fetchedId")
        Log.d(TAG, "  - Rating Source: OMDb")
        Log.d(TAG, "  - Validation Result: ${if (validationSuccess) "SUCCESS" else "FAIL"}")

        if (!validationSuccess) {
            return@withContext null
        }
        
        val yearFirstVal = year.split("-").firstOrNull()?.trim() ?: "N/A"
        
        var director = omdb.Director ?: "N/A"
        if (director == "N/A" || director.isBlank() || director.lowercase().contains("unknown")) {
            director = omdb.Writer ?: "N/A"
            if (director == "N/A" || director.isBlank() || director.lowercase().contains("unknown")) {
                director = omdb.Actors?.split(",")?.firstOrNull()?.trim() ?: "Acclaimed Cast"
            }
        }
        director = director.replace(Regex("""\s*\([^\)]+\)"""), "").trim()

        val genre = omdb.Genre ?: "Genre Unknown"
        val synopsis = omdb.Plot ?: "A cinematic presentation of $title."
        
        var imdbRating = omdb.imdbRating ?: "N/A"
        if (imdbRating != "N/A" && !imdbRating.contains("/")) {
            imdbRating = "$imdbRating/10"
        }
        
        var rottenTomatoes = "N/A"
        var metacritic = "N/A"
        
        omdb.Ratings?.forEach { rating ->
            when (rating.Source) {
                "Internet Movie Database" -> {
                    val v = rating.Value ?: "N/A"
                    if (v != "N/A") {
                        imdbRating = if (v.contains("/")) v else "$v/10"
                    }
                }
                "Rotten Tomatoes" -> rottenTomatoes = rating.Value ?: "N/A"
                "Metacritic" -> metacritic = rating.Value ?: "N/A"
            }
        }
        
        if (metacritic == "N/A") {
            val metascore = omdb.Metascore
            if (metascore != null && metascore != "N/A" && metascore.isNotBlank()) {
                metacritic = "$metascore/100"
            }
        }

        imdbRating = validateImdb(imdbRating)
        rottenTomatoes = validateRottenTomatoes(rottenTomatoes)
        metacritic = validateMetacritic(metacritic)

        val imdbVotesVal = omdb.imdbVotes ?: "N/A"
        
        var tm = omdb.tomatoMeter ?: "N/A"
        if (tm != "N/A" && tm.isNotBlank()) {
            if (!tm.contains("%") && tm.all { it.isDigit() }) tm = "$tm%"
        } else {
            tm = rottenTomatoes
        }
        
        var tum = omdb.tomatoUserMeter ?: "N/A"
        if (tum != "N/A" && tum.isNotBlank()) {
            if (!tum.contains("%") && tum.all { it.isDigit() }) tum = "$tum%"
        }

        val platforms = "N/A"

        val apiKey = customApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val prompt = """
                    We retrieved metadata for this movie/TV show from the official movie database:
                    Title: $title
                    Year: $year
                    Director: $director
                    Genre: $genre
                    Synopsis: $synopsis
                    
                    Critic Ratings/Votes:
                    - Tomatometer: $tm (${omdb.tomatoReviews ?: "N/A"} reviews)
                    - RT Critic Rating: ${omdb.tomatoRating ?: "N/A"}/10
                    - Metascore: ${omdb.Metascore ?: "N/A"}
                    - RT Critic consensus text: ${omdb.tomatoConsensus ?: "N/A"}
                    
                    Audience Ratings/Votes:
                    - IMDb Rating: $imdbRating ($imdbVotesVal votes)
                    - RT Audience Score: $tum (${omdb.tomatoUserReviews ?: "N/A"} reviews)
                    - RT Audience Rating: ${omdb.tomatoUserRating ?: "N/A"}/5

                    Perform a synthesis of reviews, critics sentiment, audience feedback, and parents guide ratings.
                    You MUST only generate:
                    - 'criticConsensus': A compelling, high-quality, singular one-line criticisms/consensus summary based on the RT Critic reviews, top critic opinions, and RT Critic consensus. Max 1 sentence. Be highly precise and articulate.
                    - 'audienceConsensus': A compelling, high-quality, singular one-line audience consensus blurb based on the IMDb user ratings/reviews and RT audience score/reviews ("what audiences say" blurb). Max 1 sentence. Be highly relatable yet precise.
                    - 'positiveSummary': Condensed overview of positive reviews/praise (from critics / audience). Max 3 sentences.
                    - 'negativeSummary': Condensed overview of general criticisms / complaints. Max 3 sentences.
                    - 'parentsGuideSex': Sex & Nudity rating (e.g. 'Mild - brief romance' or 'None')
                    - 'parentsGuideViolence': Violence & Gore rating (e.g. 'Severe - blood, shootouts' or 'None')
                    - 'parentsGuideProfanity': Profanity and Language rating (e.g. 'Moderate - cursing throughout' or 'None')
                    - 'parentsGuideDrugs': Alcohol, Drugs & Smoking rating (e.g. 'Mild - background social drinking' or 'None')
                    - 'parentsGuideIntense': Frightening & Intense Scenes rating (e.g. 'Severe - high suspense, horror jump scares' or 'None')
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    systemInstruction = Content(parts = listOf(Part(text = "You are an expert movie reviewer and parental guide generator. You synthesize qualitative movie details to yield review summaries and parents guides. CRITICAL RESTRAINTS: You must never decide which movie or TV show was selected. You must never infer, guess, or generate any numerical, star, grade, or percentage ratings (e.g. IMDb or Rotten Tomatoes ratings). You may ONLY generate qualitative review summaries, sentiment analyses, and parent guide summaries. Return a clean JSON object containing keys: positiveSummary, negativeSummary, parentsGuideSex, parentsGuideViolence, parentsGuideProfanity, parentsGuideDrugs, parentsGuideIntense, criticConsensus, audienceConsensus. Do NOT output markdown code fences or any outer formatting, just clean raw JSON."))),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.1f
                    )
                )

                Log.d(TAG, "Requesting Gemini AI for review summaries and parents guides")
                val response = api.generateContent(apiKey, request)
                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonText != null) {
                    val enrichment = moshi.adapter(EnrichmentResult::class.java).fromJson(jsonText)
                    if (enrichment != null) {
                        return@withContext MovieRatingResult(
                            title = title,
                            year = year,
                            director = director,
                            genre = genre,
                            imdb = imdbRating,
                            rottenTomatoes = tm,
                            metacritic = metacritic,
                            synopsis = synopsis,
                            positiveSummary = enrichment.positiveSummary ?: "No consensus reviews aggregated.",
                            negativeSummary = enrichment.negativeSummary ?: "No consensus criticisms aggregated.",
                            platforms = platforms,
                            parentsGuideSex = enrichment.parentsGuideSex ?: "N/A",
                            parentsGuideViolence = enrichment.parentsGuideViolence ?: "N/A",
                            parentsGuideProfanity = enrichment.parentsGuideProfanity ?: "N/A",
                            parentsGuideDrugs = enrichment.parentsGuideDrugs ?: "N/A",
                            parentsGuideIntense = enrichment.parentsGuideIntense ?: "N/A",
                            // Contextual rating presentation fields
                            imdbVotes = imdbVotesVal,
                            tomatoMeter = tm,
                            tomatoImage = omdb.tomatoImage ?: "N/A",
                            tomatoRating = omdb.tomatoRating ?: "N/A",
                            tomatoReviews = omdb.tomatoReviews ?: "N/A",
                            tomatoFresh = omdb.tomatoFresh ?: "N/A",
                            tomatoRotten = omdb.tomatoRotten ?: "N/A",
                            tomatoConsensus = omdb.tomatoConsensus ?: "N/A",
                            tomatoUserMeter = tum,
                            tomatoUserRating = omdb.tomatoUserRating ?: "N/A",
                            tomatoUserReviews = omdb.tomatoUserReviews ?: "N/A",
                            tomatoURL = omdb.tomatoURL ?: "N/A",
                            // Gemini sentiment summaries
                            criticConsensus = enrichment.criticConsensus ?: "N/A",
                            audienceConsensus = enrichment.audienceConsensus ?: "N/A"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini enrichment failed.", e)
            }
        }

        MovieRatingResult(
            title = title,
            year = year,
            director = director,
            genre = genre,
            imdb = imdbRating,
            rottenTomatoes = tm,
            metacritic = metacritic,
            synopsis = synopsis,
            positiveSummary = "No review summaries loaded.",
            negativeSummary = "No critic criticisms loaded.",
            platforms = platforms,
            parentsGuideSex = "N/A",
            parentsGuideViolence = "N/A",
            parentsGuideProfanity = "N/A",
            parentsGuideDrugs = "N/A",
            parentsGuideIntense = "N/A",
            imdbVotes = imdbVotesVal,
            tomatoMeter = tm,
            tomatoImage = omdb.tomatoImage ?: "N/A",
            tomatoRating = omdb.tomatoRating ?: "N/A",
            tomatoReviews = omdb.tomatoReviews ?: "N/A",
            tomatoFresh = omdb.tomatoFresh ?: "N/A",
            tomatoRotten = omdb.tomatoRotten ?: "N/A",
            tomatoConsensus = omdb.tomatoConsensus ?: "N/A",
            tomatoUserMeter = tum,
            tomatoUserRating = omdb.tomatoUserRating ?: "N/A",
            tomatoUserReviews = omdb.tomatoUserReviews ?: "N/A",
            tomatoURL = omdb.tomatoURL ?: "N/A",
            criticConsensus = "N/A",
            audienceConsensus = "N/A"
        )
    }

    private fun validateImdb(value: String): String {
        val trimmed = value.trim()
        if (trimmed == "N/A" || trimmed.isBlank()) return "N/A"
        // Try parsing X.Y out of X.Y/10 or similar patterns
        val cleanNumberStr = trimmed.split("/").firstOrNull()?.trim() ?: return "N/A"
        val doubleValue = cleanNumberStr.toDoubleOrNull() ?: return "N/A"
        if (doubleValue in 0.0..10.0) {
            return "$cleanNumberStr/10"
        }
        return "N/A"
    }

    private fun validateRottenTomatoes(value: String): String {
        val trimmed = value.trim()
        if (trimmed == "N/A" || trimmed.isBlank()) return "N/A"
        val cleanNumberStr = trimmed.replace("%", "").trim()
        val intValue = cleanNumberStr.toIntOrNull() ?: return "N/A"
        if (intValue in 0..100) {
            return "$intValue%"
        }
        return "N/A"
    }

    private fun validateMetacritic(value: String): String {
        val trimmed = value.trim()
        if (trimmed == "N/A" || trimmed.isBlank()) return "N/A"
        val cleanNumberStr = trimmed.split("/").firstOrNull()?.replace("%", "")?.trim() ?: return "N/A"
        val intValue = cleanNumberStr.toIntOrNull() ?: return "N/A"
        if (intValue in 0..100) {
            return "$intValue/100"
        }
        return "N/A"
    }
}
