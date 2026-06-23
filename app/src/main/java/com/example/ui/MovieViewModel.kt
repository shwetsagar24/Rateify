package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MovieDatabase
import com.example.data.MovieRatingEntity
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
import kotlinx.coroutines.launch

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

    // Live rating mappings resolved for Homepage cards to bypass massive API overload
    private val _mediaRatings = MutableStateFlow<Map<Int, MovieRatingEntity>>(emptyMap())
    val mediaRatings: StateFlow<Map<Int, MovieRatingEntity>> = _mediaRatings.asStateFlow()

    init {
        loadHomepageContent()
    }

    fun loadHomepageContent() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val all = TmdbClient.fetchTrendingAll(context)
                val movies = TmdbClient.fetchTrendingMovies(context)
                val tv = TmdbClient.fetchTrendingTv(context)
                val topMovies = TmdbClient.fetchTopRatedMovies(context)
                val topTv = TmdbClient.fetchTopRatedTv(context)
                val docs = TmdbClient.fetchTrendingDocumentaries(context)

                _trendingAll.value = all
                _trendingMovies.value = movies
                _trendingTv.value = tv
                _topRatedMovies.value = topMovies
                _topRatedTv.value = topTv
                _trendingDocumentaries.value = docs
            } catch (e: Exception) {
                Log.e("MovieViewModel", "Error loading Homepage content", e)
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
}
