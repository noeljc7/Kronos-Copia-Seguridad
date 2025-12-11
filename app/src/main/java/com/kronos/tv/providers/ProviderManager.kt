package com.kronos.tv.providers

import android.content.Context
import com.kronos.tv.ScreenLogger
import com.kronos.tv.engine.ScriptEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

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

    companion object {
        // Única lista de proveedores (Solo Nube)
        val remoteProviders = mutableListOf<KronosProvider>()
        var isRemoteLoaded = false

        suspend fun loadRemoteProviders(manifestUrl: String) = withContext(Dispatchers.IO) {
            remoteProviders.clear()
            isRemoteLoaded = false
            
            try {
                ScreenLogger.log("KRONOS", "☁️ Actualizando desde la nube...")
                
                val jsonStr = URL(manifestUrl).readText()
                val json = JSONObject(jsonStr)

                // 1. Cargar lógica (Scripts)
                if (json.has("scripts")) {
                    val scripts = json.getJSONArray("scripts")
                    ScreenLogger.log("KRONOS", "📜 Descargando ${scripts.length()} scripts...")
                    for (i in 0 until scripts.length()) {
                        ScriptEngine.loadScriptFromUrl(scripts.getString(i))
                    }
                }

                // 2. Registrar obreros (Providers)
                if (json.has("providers")) {
                    val remoteList = json.getJSONArray("providers")
                    for (i in 0 until remoteList.length()) {
                        val p = remoteList.getJSONObject(i)
                        val id = p.getString("id")
                        val name = p.getString("name")
                        
                        // Evitar duplicados por si acaso
                        if (remoteProviders.none { it.name == id }) {
                            remoteProviders.add(JsContentProvider(id, name))
                            ScreenLogger.log("KRONOS", "✅ Fuente lista: $name")
                        }
                    }
                }

                if (remoteProviders.isEmpty()) {
                    ScreenLogger.log("ALERTA", "⚠️ Manifiesto cargado pero sin proveedores.")
                } else {
                    isRemoteLoaded = true
                    ScreenLogger.log("KRONOS", "🎉 SISTEMA LISTO: ${remoteProviders.size} fuentes activas.")
                }

            } catch (e: Exception) {
                ScreenLogger.log("ERROR", "❌ Error de conexión al Manifiesto: ${e.message}")
            }
        }
    }

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
        
        if (remoteProviders.isEmpty()) {
            ScreenLogger.log("KRONOS", "⛔ Error: No hay proveedores cargados.")
            return@coroutineScope emptyList()
        }

        ScreenLogger.log("KRONOS", "⚡ Buscando simultáneamente en ${remoteProviders.size} fuentes...")
        
        // Lanzamos todos los hilos a la vez
        val deferredResults = remoteProviders.map { provider ->
            async(Dispatchers.IO) {
                try {
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
        
        // Ordenamos: 1080p primero, luego 720p, etc.
        return@coroutineScope results.sortedByDescending { it.quality }
    }
}
