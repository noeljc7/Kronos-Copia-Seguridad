package com.kronos.tv.providers

import android.content.Context
import com.kronos.tv.ScreenLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ProviderManager(context: Context) {

    // Registramos los proveedores activos
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
        
        if (activeProviders.isEmpty()) {
            ScreenLogger.log("KRONOS", "⚠️ No hay proveedores activos.")
            return@coroutineScope emptyList()
        }

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
        
        val results = deferredResults.awaitAll().flatten()
        ScreenLogger.log("KRONOS", "✅ Total enlaces encontrados: ${results.size}")
        return@coroutineScope results.sortedByDescending { it.quality }
    }
}
