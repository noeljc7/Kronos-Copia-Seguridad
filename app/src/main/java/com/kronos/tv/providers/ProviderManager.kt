package com.kronos.tv.providers

import android.content.Context
import com.kronos.tv.models.SourceLink
import com.kronos.tv.ui.AppLogger
import com.kronos.tv.utils.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ProviderManager(context: Context) {

    // LISTA DE PLUGINS ACTIVOS
    // Aquí instanciamos el proveedor con el nombre definido en Constants (sololatino)
    private val providers: List<KronosProvider> = listOf(
        PythonProvider(context, Constants.DEFAULT_PLUGIN_NAME)
    )

    suspend fun getLinks(
        tmdbId: Int,
        title: String,
        originalTitle: String,
        year: Int,
        isMovie: Boolean,
        season: Int = 0,
        episode: Int = 0
    ): List<SourceLink> = coroutineScope {
        
        AppLogger.log("MGR", "🔍 Iniciando búsqueda en ${providers.size} proveedores...")

        val deferredResults = providers.map { provider ->
            async {
                try {
                    if (isMovie) {
                        provider.getMovieLinks(tmdbId, title, originalTitle, year)
                    } else {
                        provider.getEpisodeLinks(tmdbId, title, season, episode)
                    }
                } catch (e: Exception) {
                    AppLogger.log("MGR", "❌ Error en ${provider.name}: ${e.message}")
                    emptyList<SourceLink>()
                }
            }
        }

        // Esperar a todos y aplanar la lista de listas
        val allLinks = deferredResults.awaitAll().flatten()
        
        AppLogger.log("MGR", "✅ Total enlaces encontrados: ${allLinks.size}")
        
        // Ordenar por calidad (simple) y devolver
        allLinks.sortedByDescending { it.quality }
    }
}
