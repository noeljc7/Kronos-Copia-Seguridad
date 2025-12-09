package com.kronos.tv.providers

import android.util.Log
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.providers.SourceLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object ProviderManager {

    // Lista unificada
    val providers = mutableListOf<Provider>(
        // Tus proveedores antiguos deben heredar de 'Provider' también
        // SoloLatinoProvider(), 
        // ZonaApsProvider()
    )

    private var isRemoteLoaded = false

    suspend fun loadRemoteProviders(manifestUrl: String) = withContext(Dispatchers.IO) {
        if (isRemoteLoaded) return@withContext
        try {
            val jsonStr = URL(manifestUrl).readText()
            val json = JSONObject(jsonStr)
            
            if (json.has("scripts")) {
                val scripts = json.getJSONArray("scripts")
                for (i in 0 until scripts.length()) ScriptEngine.loadScriptFromUrl(scripts.getString(i))
            }

            if (json.has("providers")) {
                val remoteList = json.getJSONArray("providers")
                for (i in 0 until remoteList.length()) {
                    val p = remoteList.getJSONObject(i)
                    val id = p.getString("id")
                    val name = p.getString("name")
                    if (providers.none { it.name == id }) {
                        providers.add(JsContentProvider(id, name))
                    }
                }
            }
            isRemoteLoaded = true
        } catch (e: Exception) { Log.e("KRONOS", "Error manifest: ${e.message}") }
    }

    // Este método es el que llama tu interfaz gráfica (SourceSelectionScreen)
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
                Log.e("KRONOS", "Error ${provider.name}: ${e.message}") 
            }
        }
        return@withContext allLinks
    }
}
