package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movie_ratings ORDER BY timestamp DESC")
    fun getAllRatingsFlow(): Flow<List<MovieRatingEntity>>

    @Query("SELECT * FROM movie_ratings WHERE title LIKE :title LIMIT 1")
    suspend fun getRatingByTitle(title: String): MovieRatingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: MovieRatingEntity): Long

    @Query("DELETE FROM movie_ratings WHERE id = :id")
    suspend fun deleteRatingById(id: Long)

    @Query("DELETE FROM movie_ratings")
    suspend fun clearAll()
}
