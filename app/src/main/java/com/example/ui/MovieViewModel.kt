package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MovieDatabase
import com.example.data.MovieRatingEntity
import com.example.data.WatchlistEntity
import com.example.data.toWatchlistEntity
import com.example.network.GeminiClient
import com.example.network.TmdbClient
import com.example.network.TmdbTarget
import com.example.network.ParentsGuideState
import com.example.network.RtRatingFetcher
import com.example.network.updateWithRTResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Success(val movie: MovieRatingEntity) : SearchUiState
    data class SelectCandidate(val query: String, val candidates: List<com.example.network.GeminiClient.CandidateWithScore>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class MovieViewModel(application: Application) : AndroidViewModel(application) {

    private val _parentsGuideState = MutableStateFlow<ParentsGuideState>(ParentsGuideState.Loading)
    val parentsGuideState: StateFlow<ParentsGuideState> = _parentsGuideState.asStateFlow()

    fun loadParentsGuide(title: String, year: String, certification: String?) {
        viewModelScope.launch {
            _parentsGuideState.value = ParentsGuideState.Loading
            Log.d("PG_FETCH", "Start: $title $year")
            try {
                val context = getApplication<Application>()
                val guide = RtRatingFetcher.loadParentsGuide(context, title, year, certification)
                _parentsGuideState.value = ParentsGuideState.Success(guide)
                Log.d("PG_FETCH", "State: Success")
            } catch (e: Exception) {
                _parentsGuideState.value = ParentsGuideState.Error(e.message ?: "Unknown error")
                Log.d("PG_FETCH", "State: Error")
            }
        }
    }

    // Ephemeral in-memory history of direct searches
    private val _searchHistory = MutableStateFlow<List<MovieRatingEntity>>(emptyList())
    val searchHistory: StateFlow<List<MovieRatingEntity>> = _searchHistory.asStateFlow()

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    // Real and Live Homepage content sections
    private val _trendingAll = MutableStateFlow<List<TmdbTarget>>(emptyList())
    val trendingAll: StateFlow<List<TmdbTarget>> = _trendingAll.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<TmdbTarget>>(emptyList())
    val trendingMovies: StateFlow<List<TmdbTarget>> = _trendingMovies.asStateFlow()

    private val _trendingTv = MutableStateFlow<List<TmdbTarget>>(emptyList())
    val trendingTv: StateFlow<List<TmdbTarget>> = _trendingTv.asStateFlow()

    private val _topRatedMovies = MutableStateFlow<List<TmdbTarget>>(emptyList())
    val topRatedMovies: StateFlow<List<TmdbTarget>> = _topRatedMovies.asStateFlow()

    private val _topRatedTv = MutableStateFlow<List<TmdbTarget>>(emptyList())
    val topRatedTv: StateFlow<List<TmdbTarget>> = _topRatedTv.asStateFlow()

    private val _trendingDocumentaries = MutableStateFlow<List<TmdbTarget>>(emptyList())
    val trendingDocumentaries: StateFlow<List<TmdbTarget>> = _trendingDocumentaries.asStateFlow()

    private val _risingNow = MutableStateFlow<List<TmdbTarget>>(emptyList())
    val risingNow: StateFlow<List<TmdbTarget>> = _risingNow.asStateFlow()

    private val _criticallyAcclaimed = MutableStateFlow<List<TmdbTarget>>(emptyList())
    val criticallyAcclaimed: StateFlow<List<TmdbTarget>> = _criticallyAcclaimed.asStateFlow()

    private val _hiddenGems = MutableStateFlow<List<TmdbTarget>>(emptyList())
    val hiddenGems: StateFlow<List<TmdbTarget>> = _hiddenGems.asStateFlow()

    // Live rating mappings resolved for Homepage cards to bypass massive API overload
    private val _mediaRatings = MutableStateFlow<Map<Int, MovieRatingEntity>>(emptyMap())
    val mediaRatings: StateFlow<Map<Int, MovieRatingEntity>> = _mediaRatings.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedMovieGenres = MutableStateFlow<Set<Int>>(emptySet())
    val selectedMovieGenres: StateFlow<Set<Int>> = _selectedMovieGenres.asStateFlow()

    private val _selectedTvGenres = MutableStateFlow<Set<Int>>(emptySet())
    val selectedTvGenres: StateFlow<Set<Int>> = _selectedTvGenres.asStateFlow()

    private val _selectedDocGenres = MutableStateFlow<Set<Int>>(emptySet())
    val selectedDocGenres: StateFlow<Set<Int>> = _selectedDocGenres.asStateFlow()

    fun toggleMovieGenre(genreId: Int) {
        val current = _selectedMovieGenres.value
        _selectedMovieGenres.value = if (current.contains(genreId)) current - genreId else current + genreId
    }

    fun toggleTvGenre(genreId: Int) {
        val current = _selectedTvGenres.value
        _selectedTvGenres.value = if (current.contains(genreId)) current - genreId else current + genreId
    }

    fun toggleDocGenre(genreId: Int) {
        val current = _selectedDocGenres.value
        _selectedDocGenres.value = if (current.contains(genreId)) current - genreId else current + genreId
    }

    fun clearMovieGenres() {
        _selectedMovieGenres.value = emptySet()
    }

    fun clearTvGenres() {
        _selectedTvGenres.value = emptySet()
    }

    fun clearDocGenres() {
        _selectedDocGenres.value = emptySet()
    }

    val platformProviderIds = mapOf(
        "All"              to null,
        "Netflix"          to 8,
        "Prime Video"      to 9,
        "JioHotstar"       to 122,
        "Disney+ Hotstar"  to 122,
        "SonyLIV"          to 237,
        "Zee5"             to 232,
        "Apple TV+"        to 350,
        "Max"              to 384,
        "Hulu"             to 15,
        "YouTube Premium"  to 188,
        "Voot"             to 121,
        "MX Player"        to 515,
        "Sun NXT"          to 309,
        "Aha"              to 532,
        "Hoichoi"          to 315,
        "Lionsgate Play"   to 561,
        "Discovery+"       to 510,
        "Paramount+"       to 531,
        "MUBI"             to 11,
        "Peacock"          to 386,
        "Shudder"          to 99,
        "Crunchyroll"      to 283
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isRegionFixed = prefs.getBoolean("region_lock_removed", false)
            if (!isRegionFixed) {
                try {
                    // 1. Clear Room DB ratings/content cache
                    MovieDatabase.getDatabase(context).movieDao().clearAll()
                    
                    // 2. Clear Tmdb SharedPreferences cache
                    val rateifyPrefs = context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE)
                    rateifyPrefs.edit().apply {
                        remove("trending_all")
                        remove("trending_all_timestamp")
                        remove("trending_movies")
                        remove("trending_movies_timestamp")
                        remove("trending_tv")
                        remove("trending_tv_timestamp")
                        remove("top_rated_movies")
                        remove("top_rated_movies_timestamp")
                        remove("top_rated_tv")
                        remove("top_rated_tv_timestamp")
                        remove("trending_documentaries")
                        remove("trending_documentaries_timestamp")
                    }.apply()
                    
                    Log.d("CACHE", "Cleared old region-locked cache")
                } catch (e: Exception) {
                    Log.e("CACHE", "Failed clearing cache on region lock removal", e)
                }
                prefs.edit().putBoolean("region_lock_removed", true).apply()
            }
            loadHomepageContent()
        }
    }

    fun loadHomepageContent() {
        loadContentForPlatform("All")
    }

    fun loadContentForPlatform(platform: String) {
        val movieGenreQuery = if (_selectedMovieGenres.value.isEmpty()) null else _selectedMovieGenres.value.joinToString("|")
        val tvGenreQuery = if (_selectedTvGenres.value.isEmpty()) null else _selectedTvGenres.value.joinToString("|")
        val docGenreQuery = if (_selectedDocGenres.value.isEmpty()) null else _selectedDocGenres.value.joinToString("|")

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            Log.d("PLATFORM", "Selected: $platform, Movie genres: $movieGenreQuery, TV genres: $tvGenreQuery, Doc genres: $docGenreQuery")
            try {
                val context = getApplication<Application>()
                val providerId = platformProviderIds[platform]

                if (platform == "All" && movieGenreQuery == null && tvGenreQuery == null && docGenreQuery == null) {
                    val allDeferred = async { TmdbClient.fetchTrendingAll(context) }
                    val moviesDeferred = async { TmdbClient.fetchTrendingMovies(context) }
                    val tvDeferred = async { TmdbClient.fetchTrendingTv(context) }
                    val docsDeferred = async { TmdbClient.fetchTrendingDocumentaries(context) }
                    val risingDeferred = async { TmdbClient.fetchRisingNow(context, providerId) }
                    val acclaimedDeferred = async { TmdbClient.fetchCriticallyAcclaimed(context, providerId) }
                    val gemsDeferred = async { TmdbClient.fetchHiddenGems(context, providerId) }

                    _trendingAll.value = allDeferred.await()
                    _trendingMovies.value = moviesDeferred.await()
                    _trendingTv.value = tvDeferred.await()
                    _trendingDocumentaries.value = docsDeferred.await()
                    _risingNow.value = risingDeferred.await()
                    _criticallyAcclaimed.value = acclaimedDeferred.await()
                    _hiddenGems.value = gemsDeferred.await()
                } else {
                    // Fetch filtered movies
                    val moviesDeferred = async { TmdbClient.fetchByPlatform(context, providerId, "Movies", movieGenreQuery) }
                    val tvDeferred = async { TmdbClient.fetchByPlatform(context, providerId, "TV Series", tvGenreQuery) }
                    val docsDeferred = async { TmdbClient.fetchByPlatform(context, providerId, "Documentaries", docGenreQuery) }
                    val risingDeferred = async { TmdbClient.fetchRisingNow(context, providerId) }
                    val acclaimedDeferred = async { TmdbClient.fetchCriticallyAcclaimed(context, providerId) }
                    val gemsDeferred = async { TmdbClient.fetchHiddenGems(context, providerId) }

                    val movies = moviesDeferred.await()
                    val tv = tvDeferred.await()
                    val docs = docsDeferred.await()

                    _trendingMovies.value = movies
                    _trendingTv.value = tv
                    _trendingDocumentaries.value = docs

                    // Fetch overall / CombinedAll selection
                    val combinedAll = if (movieGenreQuery == null && tvGenreQuery == null) {
                        TmdbClient.fetchByPlatform(context, providerId, "All")
                    } else {
                        (movies + tv).distinctBy { it.id }.sortedByDescending { it.popularity ?: 0.0 }
                    }
                    _trendingAll.value = combinedAll
                    _risingNow.value = risingDeferred.await()
                    _criticallyAcclaimed.value = acclaimedDeferred.await()
                    _hiddenGems.value = gemsDeferred.await()

                    Log.d("PLATFORM", "Fetched platform and genres successfully! Movies count: ${movies.size}, TV: ${tv.size}")
                }
            } catch (e: Exception) {
                Log.e("MovieViewModel", "Error loading content for platform $platform", e)
                _error.value = e.message ?: "Failed to load content. Please check connection."
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Dynamic, lazy load of high-fidelity ratings with 7-day Local Room DB Cache
    fun fetchRatingsForTmdbItem(item: TmdbTarget, isTv: Boolean) {
        if (_mediaRatings.value.containsKey(item.id)) return // Already loading or loaded

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val db = MovieDatabase.getDatabase(context)
            val dao = db.movieDao()

            // 1. Resolve IMDb ID
            val tmdbId = item.id
            val imdbId = TmdbClient.fetchImdbId(context, tmdbId, isTv) ?: ""
            if (imdbId.isNotBlank() && imdbId != "N/A") {
                // Check cache first
                val cached = dao.getRatingByImdbId(imdbId)
                if (cached != null && (System.currentTimeMillis() - cached.timestamp < 7 * 24 * 60 * 60 * 1000)) {
                    val current = _mediaRatings.value.toMutableMap()
                    current[tmdbId] = cached
                    _mediaRatings.value = current
                    return@launch
                }
            }

            // 2. Fetch OMDb Ratings + Gemini Summaries
            val title = item.title ?: item.name ?: "Unknown"
            val year = (item.release_date ?: item.first_air_date ?: "").split("-").firstOrNull() ?: ""
            
            val fetchResult = if (imdbId.isNotBlank() && imdbId != "N/A") {
                GeminiClient.fetchMovieReviewsByImdbId(imdbId, title, year)
            } else {
                GeminiClient.fetchMovieReviews(title)
            }

            if (fetchResult != null) {
                // Fetch Watch Providers
                val watchList = TmdbClient.fetchWatchProviders(context, tmdbId, isTv)
                val watchStr = if (watchList.isNotEmpty()) watchList.joinToString(", ") else "N/A"

                val entity = MovieRatingEntity(
                    id = System.nanoTime(),
                    title = fetchResult.title,
                    year = fetchResult.year,
                    director = fetchResult.director,
                    genre = fetchResult.genre,
                    imdb = fetchResult.imdb,
                    rottenTomatoes = fetchResult.rottenTomatoes,
                    metacritic = fetchResult.metacritic,
                    synopsis = fetchResult.synopsis,
                    positiveSummary = fetchResult.positiveSummary,
                    negativeSummary = fetchResult.negativeSummary,
                    platforms = watchStr,
                    imdbId = imdbId,
                    timestamp = System.currentTimeMillis(),
                    imdbVotes = fetchResult.imdbVotes,
                    tomatoMeter = fetchResult.tomatoMeter,
                    tomatoImage = fetchResult.tomatoImage,
                    tomatoRating = fetchResult.tomatoRating,
                    tomatoReviews = fetchResult.tomatoReviews,
                    tomatoFresh = fetchResult.tomatoFresh,
                    tomatoRotten = fetchResult.tomatoRotten,
                    tomatoConsensus = fetchResult.tomatoConsensus,
                    tomatoUserMeter = fetchResult.tomatoUserMeter,
                    tomatoUserRating = fetchResult.tomatoUserRating,
                    tomatoUserReviews = fetchResult.tomatoUserReviews,
                    tomatoURL = fetchResult.tomatoURL,
                    criticConsensus = fetchResult.criticConsensus,
                    audienceConsensus = fetchResult.audienceConsensus,
                    parentsGuideSex = fetchResult.parentsGuideSex ?: "N/A",
                    parentsGuideViolence = fetchResult.parentsGuideViolence ?: "N/A",
                    parentsGuideProfanity = fetchResult.parentsGuideProfanity ?: "N/A",
                    parentsGuideDrugs = fetchResult.parentsGuideDrugs ?: "N/A",
                    parentsGuideIntense = fetchResult.parentsGuideIntense ?: "N/A"
                )

                val updatedEntity = try {
                    val rtScore = RtRatingFetcher.fetchRTScore(context, title, year, tmdbId, !isTv)
                    entity.updateWithRTResult(rtScore)
                } catch (e: Exception) {
                    Log.e("RT_FETCH", "Override RT score failed, using default: ${e.message}")
                    entity
                }

                // Save in Database Cache
                dao.insertRating(updatedEntity)

                // Post in memory ratings mapping
                val current = _mediaRatings.value.toMutableMap()
                current[tmdbId] = updatedEntity
                _mediaRatings.value = current
            }
        }
    }

    fun searchMovie(title: String) {
        if (title.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _searchUiState.value = SearchUiState.Loading

            try {
                val candidates = GeminiClient.getRankedCandidates(title)
                
                if (candidates.isEmpty()) {
                    _searchUiState.value = SearchUiState.Error("No titles matches found for: \"$title\". Please refine your query.")
                    return@launch
                }

                val topEntry = candidates.first()
                val score = topEntry.score
                val secondScore = if (candidates.size > 1) candidates[1].score else 0

                val isExact = GeminiClient.isExactMatch(title, topEntry.candidate)
                val autoSelect = isExact && score >= 80 && (score - secondScore >= 15)

                if (autoSelect) {
                    val targetId = topEntry.candidate.imdbID ?: ""
                    val targetTitle = topEntry.candidate.Title ?: ""
                    val targetYear = topEntry.candidate.Year ?: ""
                    
                    val networkResult = GeminiClient.fetchMovieReviewsByImdbId(targetId, targetTitle, targetYear)

                    if (networkResult != null) {
                        val baseEntity = MovieRatingEntity(
                            id = System.nanoTime(),
                            title = networkResult.title,
                            year = networkResult.year,
                            director = networkResult.director,
                            genre = networkResult.genre,
                            imdb = networkResult.imdb,
                            rottenTomatoes = networkResult.rottenTomatoes,
                            metacritic = networkResult.metacritic,
                            synopsis = networkResult.synopsis,
                            positiveSummary = networkResult.positiveSummary,
                            negativeSummary = networkResult.negativeSummary,
                            platforms = networkResult.platforms,
                            imdbId = targetId,
                            timestamp = System.currentTimeMillis(),
                            imdbVotes = networkResult.imdbVotes,
                            tomatoMeter = networkResult.tomatoMeter,
                            tomatoImage = networkResult.tomatoImage,
                            tomatoRating = networkResult.tomatoRating,
                            tomatoReviews = networkResult.tomatoReviews,
                            tomatoFresh = networkResult.tomatoFresh,
                            tomatoRotten = networkResult.tomatoRotten,
                            tomatoConsensus = networkResult.tomatoConsensus,
                            tomatoUserMeter = networkResult.tomatoUserMeter,
                            tomatoUserRating = networkResult.tomatoUserRating,
                            tomatoUserReviews = networkResult.tomatoUserReviews,
                            tomatoURL = networkResult.tomatoURL,
                            criticConsensus = networkResult.criticConsensus,
                            audienceConsensus = networkResult.audienceConsensus,
                            parentsGuideSex = networkResult.parentsGuideSex ?: "N/A",
                            parentsGuideViolence = networkResult.parentsGuideViolence ?: "N/A",
                            parentsGuideProfanity = networkResult.parentsGuideProfanity ?: "N/A",
                            parentsGuideDrugs = networkResult.parentsGuideDrugs ?: "N/A",
                            parentsGuideIntense = networkResult.parentsGuideIntense ?: "N/A"
                        )

                        val entity = try {
                            val context = getApplication<Application>()
                            val tmdbId = TmdbClient.findMovieDetailsByImdbId(context, targetId)?.id ?: 0
                            val rtScore = RtRatingFetcher.fetchRTScore(context, targetTitle, targetYear, tmdbId, true)
                            baseEntity.updateWithRTResult(rtScore)
                        } catch (e: Exception) {
                            Log.e("RT_FETCH", "Override RT score failed for search: ${e.message}")
                            baseEntity
                        }

                        val currentList = _searchHistory.value.toMutableList()
                        currentList.removeAll { it.title.lowercase().trim() == entity.title.lowercase().trim() }
                        currentList.add(0, entity)
                        _searchHistory.value = currentList.take(20)

                        _searchUiState.value = SearchUiState.Success(entity)
                    } else {
                        _searchUiState.value = SearchUiState.SelectCandidate(title, candidates)
                    }
                } else {
                    _searchUiState.value = SearchUiState.SelectCandidate(title, candidates)
                }
            } catch (e: Exception) {
                Log.e("MovieViewModel", "Search error", e)
                _searchUiState.value = SearchUiState.Error("Failed to perform search: ${e.localizedMessage}")
            }
        }
    }

    fun fetchAndShowMovieById(imdbId: String, expectedTitle: String? = null, expectedYear: String? = null) {
        if (imdbId.isBlank()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _searchUiState.value = SearchUiState.Loading
            
            val networkResult = try {
                GeminiClient.fetchMovieReviewsByImdbId(imdbId, expectedTitle, expectedYear)
            } catch (e: Exception) {
                Log.e("MovieViewModel", "Network fetch by ID failed (id=$imdbId)", e)
                null
            }

            if (networkResult != null) {
                val baseEntity = MovieRatingEntity(
                    id = System.nanoTime(),
                    title = networkResult.title,
                    year = networkResult.year,
                    director = networkResult.director,
                    genre = networkResult.genre,
                    imdb = networkResult.imdb,
                    rottenTomatoes = networkResult.rottenTomatoes,
                    metacritic = networkResult.metacritic,
                    synopsis = networkResult.synopsis,
                    positiveSummary = networkResult.positiveSummary,
                    negativeSummary = networkResult.negativeSummary,
                    platforms = networkResult.platforms,
                    imdbId = imdbId,
                    timestamp = System.currentTimeMillis(),
                    imdbVotes = networkResult.imdbVotes,
                    tomatoMeter = networkResult.tomatoMeter,
                    tomatoImage = networkResult.tomatoImage,
                    tomatoRating = networkResult.tomatoRating,
                    tomatoReviews = networkResult.tomatoReviews,
                    tomatoFresh = networkResult.tomatoFresh,
                    tomatoRotten = networkResult.tomatoRotten,
                    tomatoConsensus = networkResult.tomatoConsensus,
                    tomatoUserMeter = networkResult.tomatoUserMeter,
                    tomatoUserRating = networkResult.tomatoUserRating,
                    tomatoUserReviews = networkResult.tomatoUserReviews,
                    tomatoURL = networkResult.tomatoURL,
                    criticConsensus = networkResult.criticConsensus,
                    audienceConsensus = networkResult.audienceConsensus,
                    parentsGuideSex = networkResult.parentsGuideSex ?: "N/A",
                    parentsGuideViolence = networkResult.parentsGuideViolence ?: "N/A",
                    parentsGuideProfanity = networkResult.parentsGuideProfanity ?: "N/A",
                    parentsGuideDrugs = networkResult.parentsGuideDrugs ?: "N/A",
                    parentsGuideIntense = networkResult.parentsGuideIntense ?: "N/A"
                )

                val entity = try {
                    val context = getApplication<Application>()
                    val tmdbId = TmdbClient.findMovieDetailsByImdbId(context, imdbId)?.id ?: 0
                    val rtScore = RtRatingFetcher.fetchRTScore(context, networkResult.title, networkResult.year, tmdbId, true)
                    baseEntity.updateWithRTResult(rtScore)
                } catch (e: Exception) {
                    Log.e("RT_FETCH", "Override RT score failed for show movie: ${e.message}")
                    baseEntity
                }

                val currentList = _searchHistory.value.toMutableList()
                currentList.removeAll { it.title.lowercase().trim() == entity.title.lowercase().trim() }
                currentList.add(0, entity)
                _searchHistory.value = currentList.take(20)

                _searchUiState.value = SearchUiState.Success(entity)
            } else {
                _searchUiState.value = SearchUiState.Error("Failed to fetch verified details for selected item (ID: $imdbId). Please try a different match.")
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        val currentList = _searchHistory.value.toMutableList()
        currentList.removeAll { it.id == id }
        _searchHistory.value = currentList
    }

    fun clearAllHistory() {
        _searchHistory.value = emptyList()
    }
    
    fun selectCachedMovie(movie: MovieRatingEntity) {
        if (movie.imdbId.isNotBlank() && movie.imdbId != "N/A") {
            fetchAndShowMovieById(movie.imdbId, movie.title, movie.year)
        } else {
            searchMovie(movie.title)
        }
    }

    fun resetSearchState() {
        _searchUiState.value = SearchUiState.Idle
    }

    // Watchlist persistence integration
    private val watchlistDao = MovieDatabase.getDatabase(application).watchlistDao()

    val watchlist = watchlistDao.getAllFlow()
        .stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val watchlistCount = watchlistDao.getCount()
        .stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            0
        )

    fun toggleWatchlist(item: TmdbTarget) {
        viewModelScope.launch(Dispatchers.IO) {
            val isAdded = watchlistDao.isInWatchlist(item.id)
            val titleStr = item.title ?: item.name ?: "Unknown"
            if (isAdded) {
                watchlistDao.remove(item.id)
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "Removed from Watchlist: $titleStr",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // Try to enrich with rating info from current memory ratings cache
                val rating = _mediaRatings.value[item.id]
                val entity = item.toWatchlistEntity(
                    imdbRating = rating?.imdb,
                    rtScore = rating?.rottenTomatoes,
                    streamingProvider = rating?.platforms
                )
                watchlistDao.add(entity)
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "Added to Watchlist: $titleStr",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
