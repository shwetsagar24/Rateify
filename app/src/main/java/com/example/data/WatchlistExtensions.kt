package com.example.data

import com.example.network.TmdbTarget

fun TmdbTarget.toWatchlistEntity(
    imdbRating: String? = null,
    rtScore: String? = null,
    streamingProvider: String? = null,
    streamingLogoUrl: String? = null
): WatchlistEntity {
    val isTv = this.media_type == "tv"
    val typeStr = if (isTv) "tv" else "movie"
    val titleStr = this.title ?: this.name ?: "Unknown"
    val yearStr = (this.release_date ?: this.first_air_date ?: "").split("-").firstOrNull() ?: ""
    val genresStr = this.genre_ids?.joinToString(",") ?: ""
    
    return WatchlistEntity(
        tmdbId = this.id,
        title = titleStr,
        posterUrl = "https://image.tmdb.org/t/p/w342${this.poster_path ?: ""}",
        backdropUrl = "",
        mediaType = typeStr,
        year = yearStr,
        overview = this.overview ?: "",
        voteAverage = this.vote_average ?: 0.0,
        genreIds = genresStr,
        imdbRating = imdbRating,
        rtScore = rtScore,
        streamingProvider = streamingProvider,
        streamingLogoUrl = streamingLogoUrl
    )
}

fun WatchlistEntity.toTmdbTarget(): TmdbTarget {
    val genreList = try {
        if (this.genreIds.isBlank()) emptyList() else this.genreIds.split(",").map { it.toInt() }
    } catch (e: Exception) {
        emptyList()
    }
    return TmdbTarget(
        id = this.tmdbId,
        title = if (this.mediaType == "tv") null else this.title,
        name = if (this.mediaType == "tv") this.title else null,
        overview = this.overview,
        poster_path = this.posterUrl.substringAfter("https://image.tmdb.org/t/p/w342", ""),
        media_type = this.mediaType,
        genre_ids = genreList,
        release_date = if (this.mediaType == "tv") null else this.year,
        first_air_date = if (this.mediaType == "tv") this.year else null,
        vote_average = this.voteAverage,
        popularity = 0.0
    )
}
