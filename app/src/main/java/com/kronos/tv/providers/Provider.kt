package com.kronos.tv.providers

import com.kronos.tv.models.SourceLink

// Modificamos la clase base para cumplir con tu interfaz antigua
abstract class Provider : KronosProvider {
    abstract val language: String

    // Estos métodos son los nuevos (internos)
    abstract suspend fun search(query: String): List<com.kronos.tv.models.SearchResult>
    abstract suspend fun loadStream(id: String, type: String): String?
}
