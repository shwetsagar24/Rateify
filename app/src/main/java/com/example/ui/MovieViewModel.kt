package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MovieDatabase
import com.example.data.MovieRatingEntity
import com.example.network.GeminiClient
import com.example.service.OverlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Success(val movie: MovieRatingEntity) : SearchUiState
    data class SelectCandidate(val query: String, val candidates: List<com.example.network.GeminiClient.CandidateWithScore>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class MovieViewModel(application: Application) : AndroidViewModel(application) {

    // Keep history strictly in-memory (ephemeral) as requested, no offline database syncing or persistent cache fetching
    private val _searchHistory = MutableStateFlow<List<MovieRatingEntity>>(emptyList())
    val searchHistory: StateFlow<List<MovieRatingEntity>> = _searchHistory.asStateFlow()

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    fun searchMovie(title: String) {
        if (title.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _searchUiState.value = SearchUiState.Loading

            try {
                // 1. Fetch ranked candidates first, deterministic, without querying metadata or calling Gemini
                val candidates = GeminiClient.getRankedCandidates(title)
                
                if (candidates.isEmpty()) {
                    _searchUiState.value = SearchUiState.Error("No titles matches found for: \"$title\". Please refine your query.")
                    return@launch
                }

                val topEntry = candidates.first()
                val score = topEntry.score
                val secondScore = if (candidates.size > 1) candidates[1].score else 0

                // Strict Exact Match requirement as per prompt criteria 1, 2, 3 and 6:
                val isExact = GeminiClient.isExactMatch(title, topEntry.candidate)
                val autoSelect = isExact && score >= 80 && (score - secondScore >= 15)

                Log.d("MovieViewModel", "[MOVIE MATCH AUDIT] Matching check: UserQuery=\"$title\", TopCandidate=\"${topEntry.candidate.Title}\" (ID: ${topEntry.candidate.imdbID}), Score=$score, SecondScore=$secondScore, isExact=$isExact -> AutoSelect=$autoSelect")

                if (autoSelect) {
                    val targetId = topEntry.candidate.imdbID ?: ""
                    val targetTitle = topEntry.candidate.Title ?: ""
                    val targetYear = topEntry.candidate.Year ?: ""
                    Log.d("MovieViewModel", "[MOVIE MATCH AUDIT] Auto-selecting top exact match: \"$targetTitle\" (ID: $targetId)")
                    
                    // Fetch and verify immediately
                    val networkResult = try {
                        GeminiClient.fetchMovieReviewsByImdbId(targetId, targetTitle, targetYear)
                    } catch (e: Exception) {
                        Log.e("MovieViewModel", "Auto-select verification fetch failed", e)
                        null
                    }

                    if (networkResult != null) {
                        val entity = MovieRatingEntity(
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
                            audienceConsensus = networkResult.audienceConsensus
                        )

                        val currentList = _searchHistory.value.toMutableList()
                        currentList.removeAll { it.title.lowercase().trim() == entity.title.lowercase().trim() }
                        currentList.add(0, entity)
                        _searchHistory.value = currentList.take(20)

                        _searchUiState.value = SearchUiState.Success(entity)
                    } else {
                        Log.d("MovieViewModel", "[MOVIE MATCH AUDIT] Auto-selected candidate verification failed. Falling back to manual selection.")
                        _searchUiState.value = SearchUiState.SelectCandidate(title, candidates)
                    }
                } else {
                    Log.d("MovieViewModel", "[MOVIE MATCH AUDIT] Selection screen triggered due to lack of distinct exact match or low confidence. Score=$score, NextScore=$secondScore, isExact=$isExact")
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
                Log.e("MovieViewModel", "Network fetch by ID failed (id=$imdbId, expectedTitle=$expectedTitle)", e)
                null
            }

            if (networkResult != null) {
                val entity = MovieRatingEntity(
                    id = System.nanoTime(), // Generates unique temp ID for in-memory display
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
                    audienceConsensus = networkResult.audienceConsensus
                )

                // Update in-memory history: remove duplicate (by lowercase match) and add fresh one to top
                val currentList = _searchHistory.value.toMutableList()
                currentList.removeAll { it.title.lowercase().trim() == entity.title.lowercase().trim() }
                currentList.add(0, entity)
                _searchHistory.value = currentList.take(20) // Cap to 20 recent searches

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
            Log.d("MovieViewModel", "[MOVIE MATCH AUDIT] Re-fetching cached item using selected ID: ${movie.imdbId}")
            fetchAndShowMovieById(movie.imdbId, movie.title, movie.year)
        } else {
            searchMovie(movie.title)
        }
    }

    fun resetSearchState() {
        _searchUiState.value = SearchUiState.Idle
    }
}
