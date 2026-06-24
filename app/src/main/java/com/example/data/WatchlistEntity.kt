package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val tmdbId: Int,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val mediaType: String,
    val year: String,
    val overview: String,
    val voteAverage: Double,
    val genreIds: String,
    val imdbRating: String?,
    val rtScore: String?,
    val streamingProvider: String?,
    val streamingLogoUrl: String?,
    val addedAt: Long = System.currentTimeMillis()
)
