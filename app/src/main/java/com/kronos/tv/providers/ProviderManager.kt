package com.kronos.tv.providers

import android.content.Context
import com.kronos.tv.ScreenLogger // Asegúrate de que esto no de error (está en MainActivity)
import com.kronos.tv.engine.ScriptEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

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

    // Lista de proveedores LOCALES (Assets)
    private val localProviders = mutableListOf<KronosProvider>()

    companion object {
        // Lista de proveedores REMOTOS (Nube)
        val remoteProviders = mutableListOf<KronosProvider>()
        private var isRemoteLoaded = false

        suspend fun loadRemoteProviders(manifestUrl: String) = withContext(Dispatchers.IO) {
            if (isRemoteLoaded) return@withContext
            
            try {
                ScreenLogger.log("KRONOS", "☁️ Descargando Manifest...")
                val jsonStr = URL(manifestUrl).readText()
                val json = JSONObject(jsonStr)

                // 1. Cargar Scripts JS
                if (json.has("scripts")) {
                    val scripts = json.getJSONArray("scripts")
                    ScreenLogger.log("KRONOS", "📜 Procesando ${scripts.length()} scripts...")
                    for (i in 0 until scripts.length()) {
                        val scriptUrl = scripts.getString(i)
                        ScreenLogger.log("KRONOS", "⬇️ Bajando: ${scriptUrl.substringAfterLast("/")}")
                        ScriptEngine.loadScriptFromUrl(scriptUrl)
                    }
                }

                // 2. Registrar Proveedores
                if (json.has("providers")) {
                    val remoteList = json.getJSONArray("providers")
                    for (i in 0 until remoteList.length()) {
                        val p = remoteList.getJSONObject(i)
                        val id = p.getString("id")
                        val name = p.getString("name")
                        
                        // Evitar duplicados si ya existe
                        if (remoteProviders.none { it.name == id }) {
                            remoteProviders.add(JsContentProvider(id, name))
                            ScreenLogger.log("KRONOS", "✅ PROVEEDOR REGISTRADO: $id")
                        }
                    }
                }
                isRemoteLoaded = true
                ScreenLogger.log("KRONOS", "🎉 ¡Carga remota EXITOSA!")

            } catch (e: Exception) {
                ScreenLogger.log("ERROR", "❌ FALLO NUBE: ${e.message}")
            }
        }
    }

    init {
        loadLocalDebugProvider()
    }

    private fun loadLocalDebugProvider() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Intentamos cargar SoloLatino de los assets como respaldo
                val inputStream = context.assets.open("sololatino.js")
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                inputStream.close()
                val jsCode = String(buffer, Charsets.UTF_8)

                withContext(Dispatchers.Main) {
                    ScriptEngine.loadScript(jsCode)
                    
                    val providerId = "sololatino"
                    if (localProviders.none { it.name == providerId }) {
                        localProviders.add(JsContentProvider(providerId, "SoloLatino (Local)"))
                        ScreenLogger.log("KRONOS", "💿 Provider Local Listo")
                    }
                }
            } catch (e: java.io.FileNotFoundException) {
                // Es normal si lo borraste de assets y solo usas nube
                ScreenLogger.log("INFO", "⚠️ Sin respaldo local (Usando solo nube)")
            } catch (e: Exception) {
                ScreenLogger.log("ERROR", "❌ Error Asset: ${e.message}")
            }
        }
    }

    // --- AQUÍ ESTABA EL PROBLEMA, AHORA ESTÁ CORREGIDO ---
    suspend fun getLinks(
        tmdbId: Int, 
        title: String, 
        originalTitle: String, 
        isMovie: Boolean, 
        year: Int,             
        season: Int = 0, 
        episode: Int = 0
    ): List<SourceLink> = withContext(Dispatchers.IO) {
        
        // 1. FUSIÓN DINÁMICA: Juntamos locales + remotos AL MOMENTO DE BUSCAR
        // Usamos distinctBy para que si sololatino está en los dos lados, solo salga una vez
        val activeProviders = (localProviders + remoteProviders).distinctBy { it.name }
        
        ScreenLogger.log("KRONOS", "🔍 Buscando '${title}' en ${activeProviders.size} fuentes...")
        
        val allLinks = mutableListOf<SourceLink>()

        // 2. Loop de Búsqueda
        for (provider in activeProviders) {
            try {
                ScreenLogger.log("KRONOS", "👉 Consultando a: ${provider.name}")
                
                val links = if (isMovie) {
                    provider.getMovieLinks(tmdbId, title, originalTitle, year)
                } else {
                    provider.getEpisodeLinks(tmdbId, title, season, episode)
                }
                
                if (links.isNotEmpty()) {
                    ScreenLogger.log("KRONOS", "✅ ${provider.name}: ${links.size} enlaces encontrados")
                    allLinks.addAll(links)
                } else {
                    ScreenLogger.log("KRONOS", "⚠️ ${provider.name}: Sin resultados")
                }
                
            } catch (e: Exception) {
                ScreenLogger.log("ERROR", "❌ Fallo en ${provider.name}: ${e.message}")
            }
        }
        
        // Retornar lista ordenada por calidad
        return@withContext allLinks.sortedByDescending { it.quality }
    }
}
