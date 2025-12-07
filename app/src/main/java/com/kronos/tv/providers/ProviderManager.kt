package com.kronos.tv.providers

import com.kronos.tv.engine.ScriptEngine // <--- IMPORTANTE: Importamos el motor

class ProviderManager {
    // ORDEN DE PRIORIDAD
    private val providers = listOf<KronosProvider>(
        SoloLatinoOnePieceProvider(), // Especialista One Piece
        SoloLatinoBleachProvider(),   // Especialista Bleach
        SoloLatinoProvider(),         // General
        LaMovieProvider(),            // General
        ZonaApsProvider()             // General 3
    )

    /**
     * Busca listas de enlaces (Capítulos o Películas)
     */
    suspend fun getLinks(tmdbId: Int, title: String, isMovie: Boolean, year: Int = 0, season: Int = 0, episode: Int = 0): List<SourceLink> {
        val allLinks = mutableListOf<SourceLink>()
        
        println("Kronos: Iniciando búsqueda global para $title...")
        
        for (provider in providers) {
            try {
                println("Kronos: Consultando a ${provider.name}...")
                val links = if (isMovie) {
                    provider.getMovieLinks(tmdbId, title, title, year)
                } else {
                    provider.getEpisodeLinks(tmdbId, title, season, episode)
                }
                println("Kronos: ${provider.name} encontró ${links.size} enlaces.")
                allLinks.addAll(links)
            } catch (e: Exception) {
                println("Error en provider ${provider.name}: ${e.message}")
            }
        }
        
        // Ordenar resultados
        return allLinks.sortedBy { it.language }
    }

    /**
     * NUEVA FUNCIÓN: EL RESOLVER
     * Esta función toma un enlace "sucio" (ej. voe.sx/xyz) y usa el Motor JS
     * para intentar sacar el video directo (.m3u8).
     *
     * @return El enlace directo si funciona, o el original si falla.
     */
    suspend fun resolveVideoLink(serverName: String, originalUrl: String): String {
        // Limpiamos el nombre (Ej: "Voe - 1080p" -> "Voe")
        // Esto es necesario porque el archivo en GitHub se llama "Voe", no "Voe - Latino"
        val providerKey = serverName.split(" ")[0].replace("-", "").trim()

        println("Kronos Engine: Intentando resolver $providerKey con URL: $originalUrl")

        // 1. Preguntamos al Motor (Scripts en GitHub)
        val extractedUrl = ScriptEngine.extractLink(providerKey, originalUrl)

        return if (!extractedUrl.isNullOrBlank() && extractedUrl.startsWith("http")) {
            println("Kronos Engine: ¡ÉXITO! Script funcionó.")
            extractedUrl // Devolvemos el link directo (.m3u8 / .mp4)
        } else {
            println("Kronos Engine: Script falló o no existe. Usando fallback.")
            originalUrl // Devolvemos la URL original para que el WebView lo intente
        }
    }
}
