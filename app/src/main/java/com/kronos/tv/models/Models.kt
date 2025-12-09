package com.kronos.tv.models

// SOLO DEJA ESTOS DOS AQUÍ
data class SearchResult(
    val title: String?,
    val url: String?,
    val img: String?,
    val id: String,
    val type: String?,
    val year: String? = "" // <--- Agrega este campo si no existe
)

data class Episode(
    val name: String,
    val url: String,
    val season: Int = 0,
    val episode: Int = 0
)

// ¡BORRA SourceLink DE AQUÍ!
