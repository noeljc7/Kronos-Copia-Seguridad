package com.kronos.tv.providers

import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.Episode

// Esta clase conecta el mundo JS con la interfaz KronosProvider
abstract class Provider : KronosProvider {
    abstract val language: String

    // Métodos internos JS
    abstract suspend fun search(query: String): List<SearchResult>
    abstract suspend fun loadEpisodes(url: String): List<Episode>
    abstract suspend fun loadStream(id: String, type: String): String?
}
