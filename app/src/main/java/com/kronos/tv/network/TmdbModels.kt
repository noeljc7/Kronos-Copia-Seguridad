package com.kronos.tv.network

import com.google.gson.annotations.SerializedName

// Respuesta genérica para listas
data class TmdbResponse(val results: List<TmdbMovie>)

data class GenreResponse(val genres: List<Genre>)
data class Genre(val id: Int, val name: String)

data class TmdbMovie(
    val id: Int,
    val title: String?,
    val original_title: String? = null,
    val name: String?,
    val original_name: String? = null,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double,
    val media_type: String? = "movie"
) {
    fun getDisplayTitle(): String {
        return if (!title.isNullOrBlank()) title 
        else if (!name.isNullOrBlank()) name 
        else "Sin Título"
    }
    
    fun getOriginalTitleSafe(): String {
        return if (!original_title.isNullOrBlank()) original_title 
        else if (!original_name.isNullOrBlank()) original_name 
        else getDisplayTitle()
    }
    
    fun getYearSafe(): Int {
        val date = if (!release_date.isNullOrBlank()) release_date 
                   else if (!first_air_date.isNullOrBlank()) first_air_date 
                   else null
        return date?.take(4)?.toIntOrNull() ?: 0
    }

    fun getFullPosterUrl() = if (!poster_path.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$poster_path" else "https://via.placeholder.com/500x750?text=No+Image"
    fun getFullBackdropUrl() = if (!backdrop_path.isNullOrBlank()) "https://image.tmdb.org/t/p/original$backdrop_path" else getFullPosterUrl()
    fun getOverviewSafe() = if (!overview.isNullOrBlank()) overview else "Sin descripción disponible."
}

data class ExternalIdsResponse(val imdb_id: String?)
data class TmdbSeasonResponse(val episodes: List<TmdbEpisode>)

data class TmdbEpisode(
    val episode_number: Int, 
    val name: String, 
    val overview: String?, 
    val still_path: String?
) {
    fun getThumbUrl() = if (!still_path.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$still_path" else "https://via.placeholder.com/500x280?text=No+Image"
    fun getOverviewSafe() = if (!overview.isNullOrBlank()) overview else "Sin descripción disponible."
}

data class TmdbTvDetail(
    val id: Int, 
    val name: String, 
    val overview: String?, 
    val backdrop_path: String?, 
    val poster_path: String?, 
    val seasons: List<TmdbSeason>, 
    val first_air_date: String?
) {
    fun getFullBackdropUrl() = if (!backdrop_path.isNullOrBlank()) "https://image.tmdb.org/t/p/original$backdrop_path" else "https://via.placeholder.com/1920x1080?text=No+Image"
    fun getFullPosterUrl() = if (!poster_path.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$poster_path" else "https://via.placeholder.com/500x750?text=No+Image"
    fun getOverviewSafe() = if (!overview.isNullOrBlank()) overview else "Sin descripción disponible."
}

data class TmdbSeason(
    val season_number: Int, 
    val name: String, 
    val poster_path: String?, 
    val episode_count: Int
) {
    fun getPosterUrl() = if (!poster_path.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$poster_path" else "https://via.placeholder.com/500x750?text=No+Image"
}
