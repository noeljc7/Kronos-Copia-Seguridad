package com.kronos.tv.providers

import android.util.Log
import com.kronos.tv.engine.ScriptEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class ProviderManager {

    // Lista de proveedores locales (siempre disponibles)
    private val providers = mutableListOf<KronosProvider>(
        SoloLatinoOnePieceProvider(),
        SoloLatinoBleachProvider(),
        SoloLatinoProvider(),
        LaMovieProvider(),
        ZonaApsProvider()
    )

    companion object {
        // Lista estática para guardar los proveedores JS descargados
        val remoteProviders = mutableListOf<KronosProvider>()
        private var isRemoteLoaded = false

        // ESTE ES EL MÉTODO QUE LLAMA TU MAIN ACTIVITY
        suspend fun loadRemoteProviders(manifestUrl: String) = withContext(Dispatchers.IO) {
            if (isRemoteLoaded) return@withContext
            try {
                val jsonStr = URL(manifestUrl).readText()
                val json = JSONObject(jsonStr)

                // 1. Cargar Scripts JS
                if (json.has("scripts")) {
                    val scripts = json.getJSONArray("scripts")
                    for (i in 0 until scripts.length()) {
                        ScriptEngine.loadScriptFromUrl(scripts.getString(i))
                    }
                }

                // 2. Crear Providers JS
                if (json.has("providers")) {
                    val remoteList = json.getJSONArray("providers")
                    for (i in 0 until remoteList.length()) {
                        val p = remoteList.getJSONObject(i)
                        val id = p.getString("id")
                        val name = p.getString("name")
                        
                        // Guardamos en la lista estática
                        if (remoteProviders.none { it.name == id }) {
                            remoteProviders.add(JsContentProvider(id, name))
                            Log.d("KRONOS", "Remoto cargado: $name")
                        }
                    }
                }
                isRemoteLoaded = true
            } catch (e: Exception) {
                Log.e("KRONOS", "Error carga remota: ${e.message}")
            }
        }
    }

    // Constructor: Al crear una instancia, fusionamos locales + remotos
    init {
        providers.addAll(remoteProviders)
    }

    // Tu método de búsqueda (Instancia)
    suspend fun getLinks(tmdbId: Int, title: String, isMovie: Boolean, year: Int = 0, season: Int = 0, episode: Int = 0): List<SourceLink> = withContext(Dispatchers.IO) {
        val allLinks = mutableListOf<SourceLink>()
        
        for (provider in providers) {
            try {
                val links = if (isMovie) {
                    provider.getMovieLinks(tmdbId, title, title, year)
                } else {
                    provider.getEpisodeLinks(tmdbId, title, season, episode)
                }
                allLinks.addAll(links)
            } catch (e: Exception) {
                Log.e("KRONOS", "Fallo en ${provider.name}: ${e.message}")
            }
        }
        return@withContext allLinks.sortedByDescending { it.quality }
    }
    
    suspend fun resolveVideoLink(serverName: String, originalUrl: String): String {
        return originalUrl 
    }
}
