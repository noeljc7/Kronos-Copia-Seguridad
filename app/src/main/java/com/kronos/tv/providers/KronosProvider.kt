package com.kronos.tv.providers

import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.SourceLink

interface KronosProvider {
    val name: String
    val language: String
    
    // Búsqueda general
    suspend fun search(query: String): List<SearchResult>
    
    // Obtener enlaces de Película
    suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink>
    
    // Obtener enlaces de Episodio
    suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink>
}
