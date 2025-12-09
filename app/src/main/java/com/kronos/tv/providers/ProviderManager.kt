package com.kronos.tv.providers

import android.content.Context
import android.util.Log
import com.kronos.tv.engine.ScriptEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// --- CLASE SOURCELINK ---
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

    // Lista vacía al inicio (se llena con JS local o remoto)
    private val providers = mutableListOf<KronosProvider>()

    companion object {
        val remoteProviders = mutableListOf<KronosProvider>()
        private var isRemoteLoaded = false

        suspend fun loadRemoteProviders(manifestUrl: String) = withContext(Dispatchers.IO) {
            if (isRemoteLoaded) return@withContext
            try {
                val jsonStr = URL(manifestUrl).readText()
                val json = JSONObject(jsonStr)

                if (json.has("scripts")) {
                    val scripts = json.getJSONArray("scripts")
                    for (i in 0 until scripts.length()) {
                        ScriptEngine.loadScriptFromUrl(scripts.getString(i))
                    }
                }

                if (json.has("providers")) {
                    val remoteList = json.getJSONArray("providers")
                    for (i in 0 until remoteList.length()) {
                        val p = remoteList.getJSONObject(i)
                        val id = p.getString("id")
                        val name = p.getString("name")
                        
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

    init {
        providers.addAll(remoteProviders)
        loadLocalDebugProvider()
    }

    private fun loadLocalDebugProvider() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = context.assets.open("sololatino.js")
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                inputStream.close()
                val jsCode = String(buffer, Charsets.UTF_8)

                withContext(Dispatchers.Main) {
                    ScriptEngine.loadScript(jsCode)
                    Log.d("KRONOS", "✅ JS Local Inyectado: sololatino.js")

                    val providerId = "sololatino"
                    if (providers.none { it.name == providerId }) {
                        val localJsProvider = JsContentProvider(providerId, "SoloLatino (Local)")
                        providers.add(localJsProvider)
                        if (remoteProviders.none { it.name == providerId }) {
                            remoteProviders.add(localJsProvider)
                        }
                        Log.d("KRONOS", "✅ Provider SoloLatino registrado")
                    }
                }
            } catch (e: java.io.FileNotFoundException) {
                Log.w("KRONOS", "⚠️ No se encontró 'sololatino.js' en assets.")
            } catch (e: Exception) {
                Log.e("KRONOS", "❌ Error cargando asset local: ${e.message}")
            }
        }
    }

    // --- FUNCIÓN ACTUALIZADA (Recibe originalTitle y year) ---
    suspend fun getLinks(
        tmdbId: Int, 
        title: String, 
        originalTitle: String, // <--- NUEVO
        isMovie: Boolean, 
        year: Int,             // <--- NUEVO
        season: Int = 0, 
        episode: Int = 0
    ): List<SourceLink> = withContext(Dispatchers.IO) {
        
        val allLinks = mutableListOf<SourceLink>()
        
        for (provider in providers) {
            try {
                val links = if (isMovie) {
                    // Pasamos todos los datos al provider
                    provider.getMovieLinks(tmdbId, title, originalTitle, year)
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
