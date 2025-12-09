package com.kronos.tv.providers
import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.Episode

interface KronosProvider {
    val name: String
    val language: String
    suspend fun search(query: String): List<SearchResult>
    suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink>
    suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink>
    suspend fun loadEpisodes(url: String): List<Episode>
    suspend fun loadStream(id: String, type: String): String?
}
