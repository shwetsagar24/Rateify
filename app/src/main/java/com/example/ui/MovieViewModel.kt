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

            // Always fetch freshly from the network as requested
            val networkResult = try {
                GeminiClient.fetchMovieReviews(title)
            } catch (e: Exception) {
                Log.e("MovieViewModel", "Network fetch failed", e)
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
                    timestamp = System.currentTimeMillis()
                )

                // Update in-memory history: remove duplicate (by lowercase match) and add fresh one to top
                val currentList = _searchHistory.value.toMutableList()
                currentList.removeAll { it.title.lowercase().trim() == entity.title.lowercase().trim() }
                currentList.add(0, entity)
                _searchHistory.value = currentList.take(20) // Cap to 20 recent searches

                _searchUiState.value = SearchUiState.Success(entity)
            } else {
                _searchUiState.value = SearchUiState.Error("Failed to fetch fresh live details. Please check your internet connection or API settings.")
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
        // Fetch freshly from network instead of using stored rating state
        searchMovie(movie.title)
    }

    fun resetSearchState() {
        _searchUiState.value = SearchUiState.Idle
    }
}
