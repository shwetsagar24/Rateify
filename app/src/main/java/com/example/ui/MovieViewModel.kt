package com.example.ui

import android.app.Application
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

    private val db = MovieDatabase.getDatabase(application)
    private val dao = db.movieDao()

    val searchHistory: StateFlow<List<MovieRatingEntity>> = dao.getAllRatingsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    fun searchMovie(title: String) {
        if (title.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _searchUiState.value = SearchUiState.Loading

            // 1. Check if the movie already exists in Room database cache
            val existing = dao.getRatingByTitle("%$title%")
            if (existing != null) {
                // Update timestamp to bring it to the top of search history
                val updated = existing.copy(timestamp = System.currentTimeMillis())
                dao.insertRating(updated)
                _searchUiState.value = SearchUiState.Success(updated)
                return@launch
            }

            // 2. Fetch using Gemini API
            val result = GeminiClient.fetchMovieReviews(title)
            if (result != null) {
                val entity = MovieRatingEntity(
                    title = result.title,
                    year = result.year,
                    director = result.director,
                    genre = result.genre,
                    imdb = result.imdb,
                    rottenTomatoes = result.rottenTomatoes,
                    metacritic = result.metacritic,
                    synopsis = result.synopsis,
                    positiveSummary = result.positiveSummary,
                    negativeSummary = result.negativeSummary,
                    platforms = result.platforms
                )
                dao.insertRating(entity)
                _searchUiState.value = SearchUiState.Success(entity)
            } else {
                _searchUiState.value = SearchUiState.Error("Failed to fetch cinematic review. Check your network or API key settings.")
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteRatingById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAll()
        }
    }
    
    fun selectCachedMovie(movie: MovieRatingEntity) {
        _searchUiState.value = SearchUiState.Success(movie)
    }

    fun resetSearchState() {
        _searchUiState.value = SearchUiState.Idle
    }
}
