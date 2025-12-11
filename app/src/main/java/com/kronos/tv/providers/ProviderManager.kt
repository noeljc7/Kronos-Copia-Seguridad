package com.kronos.tv.providers

import android.content.Context
import com.kronos.tv.ScreenLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

// Modelo de datos para el enlace
data class SourceLink(
    val name: String,
    val url: String,
    val quality: String,
    val language: String,
    val provider: String = "",
    val isDirect: Boolean = false,
    val requiresWebView: Boolean = false
)

class ProviderManager(private val context: Context) {

    // --- AQUÍ ESTÁ EL CAMBIO ---
    // Ya no cargamos nada de la nube.
    // Instanciamos directamente nuestro cerebro de Python.
    private val activeProviders = listOf<KronosProvider>(
        PythonProvider(context) // <--- Esta clase es la que creamos en el paso anterior
    )

    // Eliminamos el companion object de carga remota porque Python vive dentro de la App.

    // Búsqueda Optimizada (Paralela)
    suspend fun getLinks(
        tmdbId: Int, 
        title: String, 
        originalTitle: String, 
        isMovie: Boolean, 
        year: Int,              
        season: Int = 0, 
        episode: Int = 0
    ): List<SourceLink> = coroutineScope {
        
        ScreenLogger.log("KRONOS", "🐍 Iniciando motor Python...")
        
        if (activeProviders.isEmpty()) {
            ScreenLogger.log("KRONOS", "⛔ Error: No hay proveedores Python registrados.")
            return@coroutineScope emptyList()
        }

        // Lanzamos todos los hilos a la vez
        val deferredResults = activeProviders.map { provider ->
            async(Dispatchers.IO) {
                try {
                    ScreenLogger.log("KRONOS", "👉 Consultando: ${provider.name}")
                    
                    if (isMovie) {
                        provider.getMovieLinks(tmdbId, title, originalTitle, year)
                    } else {
                        provider.getEpisodeLinks(tmdbId, title, season, episode)
                    }
                } catch (e: Exception) {
                    ScreenLogger.log("ERROR", "❌ Fallo en ${provider.name}: ${e.message}")
                    emptyList<SourceLink>()
                }
            }
        }

        // Esperamos al más lento y unimos resultados
        val results = deferredResults.awaitAll().flatten()
        
        ScreenLogger.log("KRONOS", "✅ Total enlaces encontrados: ${results.size}")
        
        // Ordenamos: 1080p primero, luego 720p, etc.
        return@coroutineScope results.sortedByDescending { it.quality }
    }
}
