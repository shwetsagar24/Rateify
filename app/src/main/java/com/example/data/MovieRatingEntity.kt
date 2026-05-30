package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movie_ratings")
data class MovieRatingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val year: String,
    val director: String,
    val genre: String,
    val imdb: String,
    val rottenTomatoes: String,
    val metacritic: String,
    val synopsis: String,
    val positiveSummary: String,
    val negativeSummary: String,
    val platforms: String, // Comma separated platforms, e.g. "Netflix, Prime Video, Hotstar"
    val timestamp: Long = System.currentTimeMillis()
)
