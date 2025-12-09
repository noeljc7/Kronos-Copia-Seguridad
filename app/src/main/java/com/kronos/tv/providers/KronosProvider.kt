package com.kronos.tv.providers

import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.Episode

interface KronosProvider {
    // Identificadores básicos
    val name: String
    
    // --- ESTA FALTABA ---
    val language: String 

    // --- ESTA FALTABA ---
    suspend fun search(query: String): List<SearchResult>
    
    // Métodos principales (estos seguro ya los tenías, pero asegúrate de la firma)
    suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink>
    
    suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink>
    
    // --- ESTA FALTABA ---
    suspend fun loadEpisodes(url: String): List<Episode>
    
    // --- ESTA FALTABA ---
    suspend fun loadStream(id: String, type: String): String?
}
