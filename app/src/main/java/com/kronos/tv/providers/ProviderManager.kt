package com.kronos.tv.providers

import android.util.Log
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.providers.SourceLink // Asegura que este import exista
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// VOLVEMOS A CLASS (Como lo tenías antes)
class ProviderManager {

    // Lista base con tus proveedores nativos
    private val providers = mutableListOf<KronosProvider>(
        SoloLatinoOnePieceProvider(),
        SoloLatinoBleachProvider(),
        SoloLatinoProvider(),
        LaMovieProvider(),
        ZonaApsProvider()
    )

    // Flag estático para no cargar scripts 20 veces si creas varias instancias del Manager
    companion object {
        private var isRemoteLoaded = false
    }

    // Tu lógica original: Cargar remotos bajo demanda
    private suspend fun ensureRemotesLoaded() {
        if (isRemoteLoaded) return
        try {
            val manifestUrl = "https://raw.githubusercontent.com/noeljc7/Kronos-Copia-Seguridad/refs/heads/main/kronos_scripts/manifest.json"
            val jsonStr = URL(manifestUrl).readText()
            val json = JSONObject(jsonStr)

            // 1. Cargar Scripts JS
            if (json.has("scripts")) {
                val scripts = json.getJSONArray("scripts")
                for (i in 0 until scripts.length()) {
                    ScriptEngine.loadScriptFromUrl(scripts.getString(i))
                }
            }

            // 2. Registrar Providers JS (Solo en memoria temporalmente para esta instancia si fuera necesario, 
            // pero mejor lo cargamos al companion o lista estática si pudiéramos. 
            // Para no complicar, lo añadimos a la lista local 'providers')
            if (json.has("providers")) {
                val remoteList = json.getJSONArray("providers")
                for (i in 0 until remoteList.length()) {
                    val p = remoteList.getJSONObject(i)
                    val id = p.getString("id")
                    val name = p.getString("name")
                    // Evitar duplicados por nombre
                    if (providers.none { it.name == id }) {
                        providers.add(JsContentProvider(id, name))
                        Log.d("KRONOS", "Remoto cargado: $name")
                    }
                }
            }
            isRemoteLoaded = true
        } catch (e: Exception) {
            Log.e("KRONOS", "Error carga remota: ${e.message}")
        }
    }

    // Tu método original restaurado
    suspend fun getLinks(tmdbId: Int, title: String, isMovie: Boolean, year: Int = 0, season: Int = 0, episode: Int = 0): List<SourceLink> = withContext(Dispatchers.IO) {
        // 1. Asegurar carga (lo que tú hacías antes)
        ensureRemotesLoaded()

        val allLinks = mutableListOf<SourceLink>()
        
        // 2. Barrido de proveedores
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
    
    // Método auxiliar que usaba tu UI
    suspend fun resolveVideoLink(serverName: String, originalUrl: String): String {
        return originalUrl 
    }
}
