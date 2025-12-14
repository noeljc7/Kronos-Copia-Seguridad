package com.kronos.tv.providers

import android.content.Context
import com.kronos.tv.ScreenLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// Asegúrate de que este Manager solo use el proveedor de Python
class ProviderManager(context: Context) {

    // Instanciamos nuestro cerebro Python
    private val activeProviders = listOf<KronosProvider>(
        PythonProvider(context) 
    )

    suspend fun getLinks(
        tmdbId: Int, 
        title: String, 
        originalTitle: String, 
        isMovie: Boolean, 
        year: Int,              
        season: Int = 0, 
        episode: Int = 0
    ): List<SourceLink> = coroutineScope {
        
        if (activeProviders.isEmpty()) return@coroutineScope emptyList()

        val deferredResults = activeProviders.map { provider ->
            async(Dispatchers.IO) {
                try {
                    if (isMovie) {
                        provider.getMovieLinks(tmdbId, title, originalTitle, year)
                    } else {
                        provider.getEpisodeLinks(tmdbId, title, season, episode)
                    }
                } catch (e: Exception) {
                    ScreenLogger.log("ERROR", "Fallo en ${provider.name}: ${e.message}")
                    emptyList<SourceLink>()
                }
            }
        }
        
        // Unimos resultados y ordenamos por calidad (1080p arriba)
        val results = deferredResults.awaitAll().flatten()
        return@coroutineScope results.sortedByDescending { it.quality }
    }
}
