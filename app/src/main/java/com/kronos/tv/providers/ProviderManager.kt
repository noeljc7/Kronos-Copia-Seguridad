package com.kronos.tv.providers

class ProviderManager {
    // ORDEN DE PRIORIDAD
    private val providers = listOf<KronosProvider>(
        SoloLatinoOnePieceProvider(), // Especialista One Piece
        SoloLatinoBleachProvider(),   // <--- NUEVO ESPECIALISTA BLEACH
        SoloLatinoProvider(),         // General
        LaMovieProvider(),             // General
        ZonaApsProvider()             // General 3 (Web Scraping) <--- ¡RESUCITADO!
    )

    suspend fun getLinks(tmdbId: Int, title: String, isMovie: Boolean, year: Int = 0, season: Int = 0, episode: Int = 0): List<SourceLink> {
        val allLinks = mutableListOf<SourceLink>()
        
        println("Kronos: Iniciando búsqueda global para $title...")
        
        for (provider in providers) {
            try {
                // Optimización: Si es One Piece, el proveedor general SoloLatino fallará encontrando la temporada 20
                // porque TMDB dice S20E1 y SoloLatino espera 1x892.
                // Nuestro especialista se encargará.
                
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
}