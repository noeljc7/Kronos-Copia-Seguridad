package com.kronos.tv.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbService {
    @GET("movie/popular")
    suspend fun getPopularMovies(@Query("api_key") apiKey: String, @Query("language") language: String = "es-MX", @Query("page") page: Int = 1): TmdbResponse
    
    @GET("tv/popular")
    suspend fun getPopularTvShows(@Query("api_key") apiKey: String, @Query("language") language: String = "es-MX", @Query("page") page: Int = 1): TmdbResponse
    
    // Discover
    @GET("discover/movie")
    suspend fun getMoviesByGenre(@Query("api_key") apiKey: String, @Query("with_genres") genreId: Int, @Query("language") language: String = "es-MX", @Query("page") page: Int = 1): TmdbResponse
    
    @GET("discover/tv")
    suspend fun getTvByGenre(@Query("api_key") apiKey: String, @Query("with_genres") genreId: Int, @Query("language") language: String = "es-MX", @Query("page") page: Int = 1): TmdbResponse

    // Search
    @GET("search/movie")
    suspend fun searchMovies(@Query("api_key") apiKey: String, @Query("query") query: String, @Query("language") language: String = "es-MX"): TmdbResponse
    
    @GET("search/tv")
    suspend fun searchTvShows(@Query("api_key") apiKey: String, @Query("query") query: String, @Query("language") language: String = "es-MX"): TmdbResponse
    
    // Details & IDs
    @GET("movie/{movie_id}/external_ids")
    suspend fun getMovieExternalIds(@Path("movie_id") movieId: Int, @Query("api_key") apiKey: String): ExternalIdsResponse
    
    @GET("tv/{tv_id}/external_ids")
    suspend fun getTvExternalIds(@Path("tv_id") tvId: Int, @Query("api_key") apiKey: String): ExternalIdsResponse
    
    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetails(@Path("tv_id") tvId: Int, @Path("season_number") seasonNumber: Int, @Query("api_key") apiKey: String, @Query("language") language: String = "es-MX"): TmdbSeasonResponse
    
    @GET("tv/{tv_id}")
    suspend fun getTvShowDetails(@Path("tv_id") tvId: Int, @Query("api_key") apiKey: String, @Query("language") language: String = "es-MX"): TmdbTvDetail
    
    // Genres
    @GET("genre/movie/list")
    suspend fun getMovieGenres(@Query("api_key") apiKey: String, @Query("language") language: String = "es-MX"): GenreResponse
    
    @GET("genre/tv/list")
    suspend fun getTvGenres(@Query("api_key") apiKey: String, @Query("language") language: String = "es-MX"): GenreResponse
}
