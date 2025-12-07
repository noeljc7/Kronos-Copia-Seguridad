package com.kronos.tv.providers

import com.kronos.tv.engine.ScriptEngine
import org.json.JSONObject
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProviderManager {
    
    // Lista de proveedores (Locales + Remotos)
    private val providers = mutableListOf<KronosProvider>(
        SoloLatinoOnePieceProvider(),
        SoloLatinoBleachProvider(),
        SoloLatinoProvider(),
        LaMovieProvider(),
        ZonaApsProvider()
    )
    
    private var isRemoteLoaded = false

    /**
     * Carga proveedores dinámicos desde GitHub
     */
    suspend fun loadRemoteProviders(manifestUrl: String) = withContext(Dispatchers.IO) {
        if (isRemoteLoaded) return@withContext
        try {
            val jsonStr = URL(manifestUrl).readText()
            val json = JSONObject(jsonStr)
            
            if (json.has("search_providers")) {
                val remoteList = json.getJSONArray("search_providers")
                for (i in 0 until remoteList.length()) {
                    val providerName = remoteList.getString(i)
                    // Agregamos un proveedor puente para cada nombre en la lista
                    providers.add(JsContentProvider(providerName))
                    println("Kronos: Proveedor remoto agregado -> $providerName")
                }
            }
            isRemoteLoaded = true
        } catch (e: Exception) {
            println("Kronos: Error cargando proveedores remotos: ${e.message}")
        }
    }

    suspend fun getLinks(tmdbId: Int, title: String, isMovie: Boolean, year: Int = 0, season: Int = 0, episode: Int = 0): List<SourceLink> {
        val allLinks = mutableListOf<SourceLink>()
        
        // Aseguramos carga de remotos.
        // NOTA: Reemplaza esta URL con la tuya REAL de GitHub si es distinta
        val manifestUrl = "https://raw.githubusercontent.com/noeljc7/Kronos-Copia-Seguridad/refs/heads/main/kronos_scripts/manifest.json"
        
        if (!isRemoteLoaded) {
            loadRemoteProviders(manifestUrl)
        }

        println("Kronos: Buscando enlaces en ${providers.size} proveedores...")
        
        for (provider in providers) {
            try {
                // Pasamos los datos de TMDB a cada proveedor
                val links = if (isMovie) {
                    provider.getMovieLinks(tmdbId, title, title, year)
                } else {
                    provider.getEpisodeLinks(tmdbId, title, season, episode)
                }
                allLinks.addAll(links)
            } catch (e: Exception) {
                println("Error en provider ${provider.name}: ${e.message}")
            }
        }
        
        return allLinks.sortedBy { it.language }
    }

    suspend fun resolveVideoLink(serverName: String, originalUrl: String): String {
        val providerKey = serverName.split(" ")[0].replace("-", "").trim()
        val extractedUrl = ScriptEngine.extractLink(providerKey, originalUrl)
        return if (!extractedUrl.isNullOrBlank() && extractedUrl.startsWith("http")) extractedUrl else originalUrl
    }
}

