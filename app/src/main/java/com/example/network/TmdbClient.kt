package com.example.network

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@JsonClass(generateAdapter = true)
data class TmdbTrendingResponse(
    val results: List<TmdbTarget>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbTarget(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val media_type: String? = null,
    val genre_ids: List<Int>? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double? = null,
    val popularity: Double? = null
)

@JsonClass(generateAdapter = true)
data class TmdbWatchProvidersResponse(
    val results: Map<String, TmdbCountryProviders>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCountryProviders(
    val flatrate: List<TmdbProvider>? = null,
    val rent: List<TmdbProvider>? = null,
    val buy: List<TmdbProvider>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbProvider(
    val provider_id: Int? = null,
    val provider_name: String? = null,
    val logo_path: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbExternalIds(
    val imdb_id: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbDetailResponse(
    val genres: List<TmdbGenre>? = null,
    val overview: String? = null,
    val vote_average: Double? = null,
    val vote_count: Int? = null,
    val runtime: Int? = null
)

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    val id: Int,
    val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbMovieDetail(
    val imdb_id: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbFindResult(
    val id: Int,
    val vote_average: Double? = null,
    val vote_count: Int? = null
)

@JsonClass(generateAdapter = true)
data class TmdbFindResponse(
    val movie_results: List<TmdbFindResult>? = null,
    val tv_results: List<TmdbFindResult>? = null
)

object TmdbClient {
    private const val TAG = "TmdbClient"
    const val DEFAULT_TMDB_API_KEY = "14d10f274cb7eb2df39c4d9b4b00de93" // Work out of the box key
    
    var customApiKey: String? = null

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getApiKey(context: Context): String {
        if (customApiKey == null) {
            val sharedPrefs = context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE)
            customApiKey = sharedPrefs.getString("tmdb_api_key", null)
        }
        return customApiKey?.takeIf { it.isNotBlank() } ?: com.example.BuildConfig.TMDB_API_KEY.takeIf { it != "MY_TMDB_API_KEY" && it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY
    }

    private suspend fun makeHttpRequest(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "TMDb network error for $url", e)
            null
        }
    }

    // Cache Trending JSON inside SharedPreferences for 3 hours
    private fun getCachedData(context: Context, cacheKey: String): String? {
        val sharedPrefs = context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE)
        val timestamp = sharedPrefs.getLong("${cacheKey}_timestamp", 0)
        val currentTime = System.currentTimeMillis()
        if (currentTime - timestamp < 3 * 60 * 60 * 1000) { // 3 hours
            return sharedPrefs.getString(cacheKey, null)
        }
        return null
    }

    private fun setCachedData(context: Context, cacheKey: String, data: String) {
        val sharedPrefs = context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString(cacheKey, data)
            .putLong("${cacheKey}_timestamp", System.currentTimeMillis())
            .apply()
    }

    suspend fun fetchTrendingAll(context: Context): List<TmdbTarget> {
        return fetchListWithCache(context, "trending_all", "https://api.themoviedb.org/3/trending/all/week")
    }

    suspend fun fetchTrendingMovies(context: Context): List<TmdbTarget> {
        return fetchListWithCache(context, "trending_movies", "https://api.themoviedb.org/3/trending/movie/week")
    }

    suspend fun fetchTrendingTv(context: Context): List<TmdbTarget> {
        return fetchListWithCache(context, "trending_tv", "https://api.themoviedb.org/3/trending/tv/week")
    }

    suspend fun fetchTopRatedMovies(context: Context): List<TmdbTarget> {
        return fetchListWithCache(context, "top_rated_movies", "https://api.themoviedb.org/3/movie/top_rated")
    }

    suspend fun fetchTopRatedTv(context: Context): List<TmdbTarget> {
        return fetchListWithCache(context, "top_rated_tv", "https://api.themoviedb.org/3/tv/top_rated")
    }

    // Documentaries are fetched via discover/movie with genre code 99
    suspend fun fetchTrendingDocumentaries(context: Context): List<TmdbTarget> {
        return fetchListWithCache(context, "trending_documentaries", "https://api.themoviedb.org/3/discover/movie?with_genres=99&sort_by=popularity.desc")
    }

    private suspend fun fetchListWithCache(context: Context, cacheKey: String, baseUrl: String): List<TmdbTarget> {
        val cached = getCachedData(context, cacheKey)
        if (cached != null) {
            try {
                val adapter = moshi.adapter(TmdbTrendingResponse::class.java)
                return adapter.fromJson(cached)?.results ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed parsing cached $cacheKey", e)
            }
        }

        val apiKey = getApiKey(context)
        val connector = if (baseUrl.contains("?")) "&" else "?"
        val url = "$baseUrl${connector}api_key=$apiKey"
        
        val response = makeHttpRequest(url)
        if (response != null) {
            setCachedData(context, cacheKey, response)
            try {
                val adapter = moshi.adapter(TmdbTrendingResponse::class.java)
                return adapter.fromJson(response)?.results ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed parsing fresh $cacheKey", e)
            }
        }
        return emptyList()
    }

    suspend fun fetchImdbId(context: Context, id: Int, isTv: Boolean): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        val url = if (isTv) {
            "https://api.themoviedb.org/3/tv/$id/external_ids?api_key=$apiKey"
        } else {
            "https://api.themoviedb.org/3/movie/$id?api_key=$apiKey"
        }

        val responseStr = makeHttpRequest(url) ?: return@withContext null
        try {
            if (isTv) {
                val adapter = moshi.adapter(TmdbExternalIds::class.java)
                adapter.fromJson(responseStr)?.imdb_id
            } else {
                val adapter = moshi.adapter(TmdbMovieDetail::class.java)
                adapter.fromJson(responseStr)?.imdb_id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetching IMDb ID", e)
            null
        }
    }

    suspend fun findMovieDetailsByImdbId(context: Context, imdbId: String): TmdbFindResult? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        val url = "https://api.themoviedb.org/3/find/$imdbId?api_key=14b4f9bd4ebfa44b7b36f391f807cf1b&external_source=imdb_id"
        val responseStr = makeHttpRequest(url) ?: return@withContext null
        try {
            val adapter = moshi.adapter(TmdbFindResponse::class.java)
            val result = adapter.fromJson(responseStr)
            val movie = result?.movie_results?.firstOrNull()
            val tv = result?.tv_results?.firstOrNull()
            movie ?: tv
        } catch (e: Exception) {
            Log.e(TAG, "Failed finding TMDb movie details by IMDb ID: $imdbId", e)
            null
        }
    }

    suspend fun fetchMovieDetails(context: Context, id: Int, isTv: Boolean): TmdbDetailResponse? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        val type = if (isTv) "tv" else "movie"
        val url = "https://api.themoviedb.org/3/$type/$id?api_key=$apiKey"
        val responseStr = makeHttpRequest(url) ?: return@withContext null
        try {
            val adapter = moshi.adapter(TmdbDetailResponse::class.java)
            adapter.fromJson(responseStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetching details for $id", e)
            null
        }
    }

    suspend fun fetchWatchProviders(context: Context, id: Int, isTv: Boolean): List<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        val type = if (isTv) "tv" else "movie"
        val url = "https://api.themoviedb.org/3/$type/$id/watch/providers?api_key=$apiKey"

        val responseStr = makeHttpRequest(url) ?: return@withContext emptyList()
        try {
            val adapter = moshi.adapter(TmdbWatchProvidersResponse::class.java)
            val parsed = adapter.fromJson(responseStr)
            val results = parsed?.results ?: return@withContext emptyList()
            
            // Check India (IN) first as requested, then US, then anything else
            val indianProviders = results["IN"]
            val fallbackProviders = results["US"] ?: results.values.firstOrNull()
            
            val activeTarget = indianProviders ?: fallbackProviders
            val flatrate = activeTarget?.flatrate ?: emptyList()
            
            flatrate.mapNotNull { it.provider_name }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse watch providers", e)
            emptyList()
        }
    }

    private val providerCache = java.util.concurrent.ConcurrentHashMap<String, TmdbProvider>()

    private fun getProviderCachedData(context: Context, cacheKey: String): String? {
        val sharedPrefs = context.getSharedPreferences("ProviderPrefs", Context.MODE_PRIVATE)
        val timestamp = sharedPrefs.getLong("${cacheKey}_timestamp", 0)
        val currentTime = System.currentTimeMillis()
        if (currentTime - timestamp < 24 * 60 * 60 * 1000) { // 24 hours
            return sharedPrefs.getString(cacheKey, null)
        }
        return null
    }

    private fun setProviderCachedData(context: Context, cacheKey: String, data: String) {
        val sharedPrefs = context.getSharedPreferences("ProviderPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString(cacheKey, data)
            .putLong("${cacheKey}_timestamp", System.currentTimeMillis())
            .apply()
    }

    suspend fun fetchFirstWatchProvider(context: Context, id: Int, isTv: Boolean): TmdbProvider? = withContext(Dispatchers.IO) {
        val cacheKey = "provider_${isTv}_$id"
        val inMemory = providerCache[cacheKey]
        if (inMemory != null) return@withContext inMemory

        // Try SharedPreferences cache
        val cachedJson = getProviderCachedData(context, cacheKey)
        if (cachedJson != null) {
            try {
                val adapter = moshi.adapter(TmdbProvider::class.java)
                val provider = adapter.fromJson(cachedJson)
                if (provider != null) {
                    providerCache[cacheKey] = provider
                    return@withContext provider
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached provider", e)
            }
        }

        val apiKey = getApiKey(context)
        val type = if (isTv) "tv" else "movie"
        val url = "https://api.themoviedb.org/3/$type/$id/watch/providers?api_key=$apiKey"

        val responseStr = makeHttpRequest(url) ?: return@withContext null
        try {
            val adapter = moshi.adapter(TmdbWatchProvidersResponse::class.java)
            val parsed = adapter.fromJson(responseStr)
            val results = parsed?.results ?: return@withContext null
            
            // Check India (IN) first as requested, then US, then anything else
            val regionProviders = results["IN"] ?: results["US"] ?: results.values.firstOrNull()
            val flatrate = regionProviders?.flatrate ?: emptyList()
            val firstProvider = flatrate.firstOrNull()
            
            if (firstProvider != null) {
                // Save to SharedPreferences and in-memory map
                val providerAdapter = moshi.adapter(TmdbProvider::class.java)
                setProviderCachedData(context, cacheKey, providerAdapter.toJson(firstProvider))
                providerCache[cacheKey] = firstProvider
                firstProvider
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch watch provider logo", e)
            null
        }
    }

    // Genre helper map
    val genreMap = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Sci-Fi",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western",
        10759 to "Action & Adventure",
        10762 to "Kids",
        10763 to "News",
        10764 to "Reality",
        10765 to "Sci-Fi & Fantasy",
        10766 to "Soap",
        10767 to "Talk",
        10768 to "War & Politics"
    )

    fun getWatchRegionForProvider(providerId: Int): String {
        return when (providerId) {
            122, 237, 232, 121, 515, 309, 532, 315, 561 -> "IN" // JioHotstar, Disney+ Hotstar, SonyLIV, Zee5, Voot, MX Player, Sun NXT, Aha, Hoichoi, Lionsgate Play -> "IN"
            15, 386, 384, 99, 531, 510, 11, 350 -> "US" // Hulu, Peacock, Max, Shudder, Paramount+, Discovery+, MUBI, Apple TV+ -> "US"
            else -> "US" // Default to US (Netflix, Prime Video, etc.)
        }
    }

    fun mapGenres(ids: List<Int>?): String {
        if (ids == null || ids.isEmpty()) return "General"
        return ids.mapNotNull { genreMap[it] }.take(3).joinToString(", ")
    }

    suspend fun discoverMovies(context: Context, providerId: Int?, page: Int, withGenres: String? = null): List<TmdbTarget> {
        val apiKey = getApiKey(context)
        var url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&sort_by=popularity.desc&page=$page"
        if (providerId != null && providerId > 0) {
            val region = getWatchRegionForProvider(providerId)
            url += "&with_watch_providers=$providerId&watch_region=$region"
        }
        if (withGenres != null) {
            url += "&with_genres=$withGenres"
        }
        val response = makeHttpRequest(url) ?: return emptyList()
        return try {
            val adapter = moshi.adapter(TmdbTrendingResponse::class.java)
            adapter.fromJson(response)?.results ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing discoverMovies", e)
            emptyList()
        }
    }

    suspend fun discoverTv(context: Context, providerId: Int?, page: Int, withGenres: String? = null): List<TmdbTarget> {
        val apiKey = getApiKey(context)
        var url = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&sort_by=popularity.desc&page=$page"
        if (providerId != null && providerId > 0) {
            val region = getWatchRegionForProvider(providerId)
            url += "&with_watch_providers=$providerId&watch_region=$region"
        }
        if (withGenres != null) {
            url += "&with_genres=$withGenres"
        }
        val response = makeHttpRequest(url) ?: return emptyList()
        return try {
            val adapter = moshi.adapter(TmdbTrendingResponse::class.java)
            adapter.fromJson(response)?.results ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing discoverTv", e)
            emptyList()
        }
    }

    suspend fun fetchByPlatform(
        context: Context,
        providerId: Int?,
        contentType: String,
        withGenres: String? = null
    ): List<TmdbTarget> = withContext(Dispatchers.IO) {
        if (providerId == null && withGenres == null) {
            return@withContext when (contentType) {
                "Movies" -> fetchTrendingMovies(context)
                "TV Series" -> fetchTrendingTv(context)
                "Documentaries" -> fetchTrendingDocumentaries(context)
                else -> fetchTrendingAll(context)
            }
        }

        try {
            val jobs = supervisorScope {
                val pages = listOf(1, 2, 3)
                when (contentType) {
                    "Movies" -> {
                        val deferreds = pages.map { page ->
                            this@supervisorScope.async {
                                discoverMovies(context, providerId, page, withGenres).map { it.copy(media_type = "movie") }
                            }
                        }
                        deferreds.map { it.await() }.flatten()
                    }
                    "TV Series" -> {
                        val deferreds = pages.map { page ->
                            this@supervisorScope.async {
                                discoverTv(context, providerId, page, withGenres).map { it.copy(media_type = "tv") }
                            }
                        }
                        deferreds.map { it.await() }.flatten()
                    }
                    "Documentaries" -> {
                        val deferreds = pages.map { page ->
                            this@supervisorScope.async {
                                val genresParam = if (withGenres != null) "99,($withGenres)" else "99"
                                discoverMovies(context, providerId, page, withGenres = genresParam).map { it.copy(media_type = "movie") }
                            }
                        }
                        deferreds.map { it.await() }.flatten()
                    }
                    else -> { // "All"
                        val movieDeferreds = pages.map { page ->
                            this@supervisorScope.async {
                                discoverMovies(context, providerId, page, withGenres).map { it.copy(media_type = "movie") }
                            }
                        }
                        val tvDeferreds = pages.map { page ->
                            this@supervisorScope.async {
                                discoverTv(context, providerId, page, withGenres).map { it.copy(media_type = "tv") }
                            }
                        }
                        val allMovies = movieDeferreds.map { it.await() }.flatten()
                        val allTv = tvDeferreds.map { it.await() }.flatten()
                        (allMovies + allTv)
                    }
                }
            }
            val results = jobs.distinctBy { it.id }.sortedByDescending { it.popularity ?: 0.0 }
            if (results.isEmpty()) {
                Log.w(TAG, "Platform discovery returned 0 results, falling back to popular content")
                when (contentType) {
                    "Movies" -> fetchTrendingMovies(context)
                    "TV Series" -> fetchTrendingTv(context)
                    "Documentaries" -> fetchTrendingDocumentaries(context)
                    else -> fetchTrendingAll(context)
                }
            } else {
                results
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetchByPlatform: ${e.message}", e)
            emptyList()
        }
    }

    fun getDateMonthsAgo(months: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -months)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun getMonthsSinceRelease(dateStr: String?): Double {
        if (dateStr.isNullOrEmpty()) return 18.0
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val releaseDate = sdf.parse(dateStr) ?: return 18.0
            val diffMs = System.currentTimeMillis() - releaseDate.time
            val diffMonths = diffMs / (1000.0 * 60.0 * 60.0 * 24.0 * 30.44)
            if (diffMonths < 0) 0.0 else diffMonths
        } catch (e: Exception) {
            18.0
        }
    }

    suspend fun fetchRisingNow(context: Context, providerId: Int? = null): List<TmdbTarget> {
        val eighteenMonthsAgo = getDateMonthsAgo(18)
        var moviesUrl = "https://api.themoviedb.org/3/discover/movie?sort_by=popularity.desc&primary_release_date.gte=$eighteenMonthsAgo&vote_count.gte=100"
        var tvUrl = "https://api.themoviedb.org/3/discover/tv?sort_by=popularity.desc&first_air_date.gte=$eighteenMonthsAgo&vote_count.gte=50"
        if (providerId != null && providerId > 0) {
            val region = getWatchRegionForProvider(providerId)
            val providerParams = "&with_watch_providers=$providerId&watch_region=$region"
            moviesUrl += providerParams
            tvUrl += providerParams
        }
        
        val suffix = if (providerId != null) "_$providerId" else ""
        val movies = fetchListWithCache(context, "rising_movies$suffix", moviesUrl).map { it.copy(media_type = "movie") }
        val tv = fetchListWithCache(context, "rising_tv$suffix", tvUrl).map { it.copy(media_type = "tv") }
        
        val combined = (movies + tv).map { item ->
            val releaseDateStr = item.release_date ?: item.first_air_date
            val months = getMonthsSinceRelease(releaseDateStr)
            val popularity = item.popularity ?: 0.0
            val risingScore = popularity / (months + 1.0)
            item to risingScore
        }.sortedByDescending { it.second }.map { it.first }.take(10)
        
        return combined
    }

    suspend fun fetchCriticallyAcclaimed(context: Context, providerId: Int? = null): List<TmdbTarget> {
        val threeYearsAgo = getDateMonthsAgo(36)
        var moviesUrl = "https://api.themoviedb.org/3/discover/movie?sort_by=vote_average.desc&vote_average.gte=7.5&vote_count.gte=500&primary_release_date.gte=$threeYearsAgo"
        var tvUrl = "https://api.themoviedb.org/3/discover/tv?sort_by=vote_average.desc&vote_average.gte=7.5&vote_count.gte=200&first_air_date.gte=$threeYearsAgo"
        if (providerId != null && providerId > 0) {
            val region = getWatchRegionForProvider(providerId)
            val providerParams = "&with_watch_providers=$providerId&watch_region=$region"
            moviesUrl += providerParams
            tvUrl += providerParams
        }
        
        val suffix = if (providerId != null) "_$providerId" else ""
        val movies = fetchListWithCache(context, "acclaimed_movies$suffix", moviesUrl).map { it.copy(media_type = "movie") }
        val tv = fetchListWithCache(context, "acclaimed_tv$suffix", tvUrl).map { it.copy(media_type = "tv") }
        
        val combined = (movies + tv).sortedByDescending { it.vote_average ?: 0.0 }.take(15)
        return combined
    }

    suspend fun fetchHiddenGems(context: Context, providerId: Int? = null): List<TmdbTarget> {
        val fiveYearsAgo = getDateMonthsAgo(60)
        var moviesUrl = "https://api.themoviedb.org/3/discover/movie?sort_by=vote_average.desc&vote_average.gte=7.0&vote_count.gte=100&vote_count.lte=2000&primary_release_date.gte=$fiveYearsAgo"
        var tvUrl = "https://api.themoviedb.org/3/discover/tv?sort_by=vote_average.desc&vote_average.gte=7.0&vote_count.gte=50&vote_count.lte=1000&first_air_date.gte=$fiveYearsAgo"
        if (providerId != null && providerId > 0) {
            val region = getWatchRegionForProvider(providerId)
            val providerParams = "&with_watch_providers=$providerId&watch_region=$region"
            moviesUrl += providerParams
            tvUrl += providerParams
        }
        
        val suffix = if (providerId != null) "_$providerId" else ""
        val movies = fetchListWithCache(context, "gems_movies$suffix", moviesUrl).map { it.copy(media_type = "movie") }
        val tv = fetchListWithCache(context, "gems_tv$suffix", tvUrl).map { it.copy(media_type = "tv") }
        
        val combined = (movies + tv).filter { (it.popularity ?: 0.0) < 50.0 }.map { item ->
            val voteAverage = item.vote_average ?: 0.0
            val popularity = item.popularity ?: 0.0
            val gemScore = voteAverage * 10.0 - (popularity * 0.5)
            item to gemScore
        }.sortedByDescending { it.second }.map { it.first }.take(15)
        
        return combined
    }
}
