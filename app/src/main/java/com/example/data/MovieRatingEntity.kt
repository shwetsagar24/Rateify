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
    val imdbId: String = "N/A",
    val timestamp: Long = System.currentTimeMillis(),
    
    // Contextual rating presentation fields
    val imdbVotes: String = "N/A",
    val tomatoMeter: String = "N/A",
    val tomatoImage: String = "N/A",
    val tomatoRating: String = "N/A",
    val tomatoReviews: String = "N/A",
    val tomatoFresh: String = "N/A",
    val tomatoRotten: String = "N/A",
    val tomatoConsensus: String = "N/A",
    val tomatoUserMeter: String = "N/A",
    val tomatoUserRating: String = "N/A",
    val tomatoUserReviews: String = "N/A",
    val tomatoURL: String = "N/A",
    
    // Gemini-powered sentiment summary fields
    val criticConsensus: String = "N/A",
    val audienceConsensus: String = "N/A"
)
