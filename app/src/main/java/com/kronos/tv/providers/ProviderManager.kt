package com.kronos.tv.providers

import android.content.Context
import android.util.Log
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.models.SourceLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// OJO: Agregamos 'private val context: Context' al constructor
class ProviderManager(private val context: Context) {

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

        // ESTE ES EL MÉTODO QUE LLAMA TU MAIN ACTIVITY (Para carga remota real)
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

    // Constructor: Al crear una instancia, fusionamos locales + remotos + ASSETS
    init {
        // 1. Agregar los remotos que ya se hayan descargado
        providers.addAll(remoteProviders)

        // 2. CARGA LOCAL DE EMERGENCIA (Desde assets/sololatino.js)
        loadLocalDebugProvider()
    }

    /**
     * Esta función lee el archivo JS local y lo inyecta.
     * Útil para desarrollo sin servidor.
     */
    private fun loadLocalDebugProvider() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Intentamos leer el archivo desde assets
                // Asegúrate de que el archivo se llame EXACTAMENTE "sololatino.js" en src/main/assets
                val inputStream = context.assets.open("sololatino.js")
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                inputStream.close()
                val jsCode = String(buffer, Charsets.UTF_8)

                withContext(Dispatchers.Main) {
                    // Inyectamos el código al WebView
                    ScriptEngine.loadScript(jsCode)
                    Log.d("KRONOS", "✅ JS Local Inyectado: sololatino.js")

                    // Registramos manualmente el proveedor JS en la lista de esta instancia
                    val providerId = "sololatino"
                    
                    // Solo lo agregamos si no existe ya
                    if (providers.none { it.name == providerId }) {
                        val localJsProvider = JsContentProvider(providerId, "SoloLatino (Local)")
                        providers.add(localJsProvider)
                        
                        // Opcional: Agregarlo a la estática también
                        if (remoteProviders.none { it.name == providerId }) {
                            remoteProviders.add(localJsProvider)
                        }
                        
                        Log.d("KRONOS", "✅ Provider SoloLatino registrado y listo para usar")
                    }
                }
            } catch (e: java.io.FileNotFoundException) {
                Log.w("KRONOS", "⚠️ No se encontró 'sololatino.js' en assets. Saltando carga local.")
            } catch (e: Exception) {
                Log.e("KRONOS", "❌ Error cargando asset local: ${e.message}")
                e.printStackTrace()
            }
        }
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
