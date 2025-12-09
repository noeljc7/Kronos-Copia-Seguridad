package com.kronos.tv.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class TmdbResponse(val results: List<TmdbMovie>)
data class GenreResponse(val genres: List<Genre>)
data class Genre(val id: Int, val name: String)

data class TmdbMovie(
    val id: Int,
    val title: String?,
    val original_title: String? = null, // <--- AGREGADO "= null" (Opcional)
    val name: String?,
    val original_name: String? = null,  // <--- AGREGADO "= null" (Opcional)
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val release_date: String? = null,   // <--- AGREGADO "= null" (Opcional)
    val first_air_date: String? = null, // <--- AGREGADO "= null" (Opcional)
    val vote_average: Double,
    val media_type: String? = "movie"
) {
    fun getDisplayTitle(): String = title ?: name ?: "Sin Título"
    
    fun getOriginalTitleSafe(): String = original_title ?: original_name ?: getDisplayTitle()
    
    fun getYearSafe(): Int {
        val date = release_date ?: first_air_date
        return date?.take(4)?.toIntOrNull() ?: 0
    }

    fun getFullPosterUrl() = if (poster_path != null) "https://image.tmdb.org/t/p/w500$poster_path" else "https://via.placeholder.com/500x750?text=No+Image"
    fun getFullBackdropUrl() = if (backdrop_path != null) "https://image.tmdb.org/t/p/original$backdrop_path" else getFullPosterUrl()
    fun getOverviewSafe() = if (!overview.isNullOrBlank()) overview else "Sin descripción disponible en español."
}

data class ExternalIdsResponse(val imdb_id: String?)
data class TmdbSeasonResponse(val episodes: List<TmdbEpisode>)
data class TmdbEpisode(val episode_number: Int, val name: String, val overview: String?, val still_path: String?) {
    fun getThumbUrl() = if (still_path != null) "https://image.tmdb.org/t/p/w500$still_path" else "https://via.placeholder.com/500x280?text=No+Image"
    fun getOverviewSafe() = if (!overview.isNullOrBlank()) overview else "Sin descripción disponible."
}

data class TmdbTvDetail(val id: Int, val name: String, val overview: String?, val backdrop_path: String?, val poster_path: String?, val seasons: List<TmdbSeason>, val first_air_date: String?) {
    fun getFullBackdropUrl() = if (backdrop_path != null) "https://image.tmdb.org/t/p/original$backdrop_path" else "https://via.placeholder.com/1920x1080?text=No+Image"
    fun getFullPosterUrl() = if (poster_path != null) "https://image.tmdb.org/t/p/w500$poster_path" else "https://via.placeholder.com/500x750?text=No+Image"
    fun getOverviewSafe() = if (!overview.isNullOrBlank()) overview else "Sin descripción disponible."
}
data class TmdbSeason(val season_number: Int, val name: String, val poster_path: String?, val episode_count: Int) {
    fun getPosterUrl() = if (poster_path != null) "https://image.tmdb.org/t/p/w500$poster_path" else "https://via.placeholder.com/500x750?text=No+Image"
}

interface TmdbService {
    @GET("movie/popular")
    suspend fun getPopularMovies(@Query("api_key") apiKey: String, @Query("language") language: String = "es-MX", @Query("page") page: Int = 1): TmdbResponse
    @GET("tv/popular")
    suspend fun getPopularTvShows(@Query("api_key") apiKey: String, @Query("language") language: String = "es-MX", @Query("page") page: Int = 1): TmdbResponse
    
    @GET("genre/movie/list")
    suspend fun getMovieGenres(@Query("api_key") apiKey: String, @Query("language") language: String = "es-MX"): GenreResponse
    @GET("genre/tv/list")
    suspend fun getTvGenres(@Query("api_key") apiKey: String, @Query("language") language: String = "es-MX"): GenreResponse

    @GET("discover/movie")
    suspend fun getMoviesByGenre(@Query("api_key") apiKey: String, @Query("with_genres") genreId: Int, @Query("language") language: String = "es-MX", @Query("page") page: Int = 1): TmdbResponse
    @GET("discover/tv")
    suspend fun getTvByGenre(@Query("api_key") apiKey: String, @Query("with_genres") genreId: Int, @Query("language") language: String = "es-MX", @Query("page") page: Int = 1): TmdbResponse

    @GET("search/movie")
    suspend fun searchMovies(@Query("api_key") apiKey: String, @Query("query") query: String, @Query("language") language: String = "es-MX"): TmdbResponse
    @GET("search/tv")
    suspend fun searchTvShows(@Query("api_key") apiKey: String, @Query("query") query: String, @Query("language") language: String = "es-MX"): TmdbResponse
    @GET("movie/{movie_id}/external_ids")
    suspend fun getMovieExternalIds(@Path("movie_id") movieId: Int, @Query("api_key") apiKey: String): ExternalIdsResponse
    @GET("tv/{tv_id}/external_ids")
    suspend fun getTvExternalIds(@Path("tv_id") tvId: Int, @Query("api_key") apiKey: String): ExternalIdsResponse
    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetails(@Path("tv_id") tvId: Int, @Path("season_number") seasonNumber: Int, @Query("api_key") apiKey: String, @Query("language") language: String = "es-MX"): TmdbSeasonResponse
    @GET("tv/{tv_id}")
    suspend fun getTvShowDetails(@Path("tv_id") tvId: Int, @Query("api_key") apiKey: String, @Query("language") language: String = "es-MX"): TmdbTvDetail
}

object RetrofitInstance {
    private const val BASE_URL = "https://api.themoviedb.org/3/"
    private const val API_KEY = "688d65c7c7e7995438db052275db288d" // Tu API Key
    private val retrofit by lazy { Retrofit.Builder().baseUrl(BASE_URL).addConverterFactory(GsonConverterFactory.create()).build() }
    val api: TmdbService by lazy { retrofit.create(TmdbService::class.java) }
    fun getApiKey(): String = API_KEY
}
