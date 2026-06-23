package com.example.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: ResponseSchema? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class ResponseSchema(
    val type: String,
    val properties: Map<String, SchemaProperty>? = null,
    val required: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class SchemaProperty(
    val type: String,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

// The structure of the film rating object we request from Gemini
@JsonClass(generateAdapter = true)
data class MovieRatingResult(
    val title: String = "",
    val year: String = "",
    val director: String = "",
    val genre: String = "",
    val imdb: String = "N/A",
    val rottenTomatoes: String = "N/A",
    val metacritic: String = "N/A",
    val synopsis: String = "",
    val positiveSummary: String = "",
    val negativeSummary: String = "",
    val platforms: String = "", // e.g., "Netflix, Prime Video"
    val parentsGuideSex: String? = "None",
    val parentsGuideViolence: String? = "None",
    val parentsGuideProfanity: String? = "None",
    val parentsGuideDrugs: String? = "None",
    val parentsGuideIntense: String? = "None",
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
    val audienceConsensus: String = "N/A",
    val rated: String = "N/A"
)

@JsonClass(generateAdapter = true)
data class OmdbResponse(
    val Title: String? = null,
    val Year: String? = null,
    val Rated: String? = null,
    val Released: String? = null,
    val Runtime: String? = null,
    val Genre: String? = null,
    val Director: String? = null,
    val Writer: String? = null,
    val Actors: String? = null,
    val Plot: String? = null,
    val Language: String? = null,
    val Country: String? = null,
    val Awards: String? = null,
    val Poster: String? = null,
    val Ratings: List<OmdbRating>? = null,
    val Metascore: String? = null,
    val imdbRating: String? = null,
    val imdbVotes: String? = null,
    val imdbID: String? = null,
    val Type: String? = null,
    val Response: String? = null,
    val Error: String? = null,
    // RT Tomatoes (tomatoes=true) fields
    val tomatoMeter: String? = null,
    val tomatoImage: String? = null,
    val tomatoRating: String? = null,
    val tomatoReviews: String? = null,
    val tomatoFresh: String? = null,
    val tomatoRotten: String? = null,
    val tomatoConsensus: String? = null,
    val tomatoUserMeter: String? = null,
    val tomatoUserRating: String? = null,
    val tomatoUserReviews: String? = null,
    val tomatoURL: String? = null
)

@JsonClass(generateAdapter = true)
data class OmdbRating(
    val Source: String? = null,
    val Value: String? = null
)

@JsonClass(generateAdapter = true)
data class OmdbSearchResult(
    val Title: String? = null,
    val Year: String? = null,
    val imdbID: String? = null,
    val Type: String? = null,
    val Poster: String? = null
)

@JsonClass(generateAdapter = true)
data class OmdbSearchResponse(
    val Search: List<OmdbSearchResult>? = null,
    val totalResults: String? = null,
    val Response: String? = null,
    val Error: String? = null
)

@JsonClass(generateAdapter = true)
data class EnrichmentResult(
    val positiveSummary: String? = null,
    val negativeSummary: String? = null,
    val platforms: String? = null,
    val parentsGuideSex: String? = null,
    val parentsGuideViolence: String? = null,
    val parentsGuideProfanity: String? = null,
    val parentsGuideDrugs: String? = null,
    val parentsGuideIntense: String? = null,
    val criticConsensus: String? = null,
    val audienceConsensus: String? = null
)


