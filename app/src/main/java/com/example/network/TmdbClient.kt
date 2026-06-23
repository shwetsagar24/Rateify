package com.example.network

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val vote_average: Double? = null
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

    fun mapGenres(ids: List<Int>?): String {
        if (ids == null || ids.isEmpty()) return "General"
        return ids.mapNotNull { genreMap[it] }.take(3).joinToString(", ")
    }
}
