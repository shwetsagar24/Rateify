package com.example.service

import com.example.network.MovieRatingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayState {
    private val _currentStreamingApp = MutableStateFlow<String?>(null)
    val currentStreamingApp = _currentStreamingApp.asStateFlow()

    private val _detectedTitles = MutableStateFlow<List<String>>(emptyList())
    val detectedTitles = _detectedTitles.asStateFlow()

    private val _selectedTitle = MutableStateFlow<String?>(null)
    val selectedTitle = _selectedTitle.asStateFlow()

    private val _ratingResult = MutableStateFlow<MovieRatingResult?>(null)
    val ratingResult = _ratingResult.asStateFlow()

    private val _isOverlayLoading = MutableStateFlow(false)
    val isOverlayLoading = _isOverlayLoading.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible = _isOverlayVisible.asStateFlow()

    private val _isDragging = MutableStateFlow(false)
    val isDragging = _isDragging.asStateFlow()

    private val _dragY = MutableStateFlow(0)
    val dragY = _dragY.asStateFlow()

    fun setStreamingApp(app: String?) {
        _currentStreamingApp.value = app
    }

    fun setDetectedTitles(titles: List<String>) {
        // Distinct values, order preserved, max 5 elements to keep UX clean
        _detectedTitles.value = titles.distinct().take(5)
    }

    fun selectTitle(title: String?) {
        _selectedTitle.value = title
    }

    fun setRatingResult(result: MovieRatingResult?) {
        _ratingResult.value = result
    }

    fun setOverlayLoading(loading: Boolean) {
        _isOverlayLoading.value = loading
    }

    fun setOverlayVisible(visible: Boolean) {
        _isOverlayVisible.value = visible
    }

    fun updateDragState(dragging: Boolean, y: Int) {
        _isDragging.value = dragging
        _dragY.value = y
    }
}
