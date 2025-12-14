package com.kronos.tv.models

data class SearchResult(
    val title: String?,
    val url: String?,
    val img: String?,
    val id: String,
    val type: String?,
    val year: String? = ""
)

data class Episode(
    val name: String,
    val url: String,
    val season: Int = 0,
    val episode: Int = 0
)

// Reintegrado aquí por arquitectura limpia. 
// Define qué es un enlace para toda la app (UI, Player y Python).
data class SourceLink(
    val name: String,         // Ej: "Fembed", "Streamtape"
    val url: String,          // La URL final
    val quality: String,      // "720p", "1080p"
    val language: String,     // "Latino", "Subtitulado"
    val provider: String = "", // Qué plugin lo encontró (Ej: "SoloLatino")
    val isDirect: Boolean = false, // Si es .mp4/.m3u8 directo
    val requiresWebView: Boolean = false,
    val headers: Map<String, String>? = null // Vital para enviar Referer/User-Agent al reproductor si es necesario
)
