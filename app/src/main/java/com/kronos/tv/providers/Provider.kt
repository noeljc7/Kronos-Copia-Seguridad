package com.kronos.tv.providers

data class SourceLink(
    val name: String,
    val url: String,
    val quality: String,
    val language: String,
    val iconUrl: String? = null,
    val isDirect: Boolean = false, // True = ExoPlayer, False = WebView
    val requiresWebView: Boolean = false // <--- NUEVO CAMPO IMPORTANTE
)

interface KronosProvider {
    val name: String
    suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink>
    suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink>
}