package com.kronos.tv.models

// Resultado de una búsqueda (Película o Serie)
data class SearchResult(
    val title: String,
    val url: String,
    val img: String,
    val id: String,
    val type: String // "movie" o "tv"
)

// Un episodio individual
data class Episode(
    val name: String,
    val url: String,
    val season: Int = 0,
    val episode: Int = 0
)

// Un enlace de video final (para el reproductor)
data class SourceLink(
    val url: String,
    val quality: String = "HD",
    val language: String = "Latino",
    val provider: String = "Unknown"
)
