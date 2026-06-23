package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "rt_cache")
data class RTCacheEntity(
    @PrimaryKey val key: String,
    val tomatometer: Int?,
    val audienceScore: Int?,
    val status: String?,
    val consensus: String?,
    val source: String,
    val cachedAt: Long
)

@Dao
interface RTCacheDao {
    @Query("SELECT * FROM rt_cache WHERE `key` = :id AND cachedAt > :expiry")
    suspend fun get(id: String, expiry: Long): RTCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RTCacheEntity)
}

@Entity(tableName = "parents_guide_cache")
data class ParentsGuideCacheEntity(
    @PrimaryKey val titleKey: String,
    val sexNudityRating: String,
    val sexNudityDesc: String,
    val violenceRating: String,
    val violenceDesc: String,
    val profanityRating: String,
    val profanityDesc: String,
    val substanceRating: String,
    val substanceDesc: String,
    val frighteningRating: String,
    val frighteningDesc: String,
    val overallRating: String,
    val certificationIndia: String,
    val minAge: Int,
    val summary: String,
    val cachedAt: Long
)

@Dao
interface ParentsGuideDao {
    @Query("SELECT * FROM parents_guide_cache WHERE titleKey = :id AND cachedAt > :expiry")
    suspend fun get(id: String, expiry: Long): ParentsGuideCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ParentsGuideCacheEntity)
}
