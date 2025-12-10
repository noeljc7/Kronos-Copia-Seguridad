package com.kronos.tv.providers

import android.content.Context
import android.util.Log
import com.kronos.tv.ScreenLogger // Importamos el Logger de MainActivity
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

    private val providers = mutableListOf<KronosProvider>()

    companion object {
        val remoteProviders = mutableListOf<KronosProvider>()
        private var isRemoteLoaded = false

        suspend fun loadRemoteProviders(manifestUrl: String) = withContext(Dispatchers.IO) {
            if (isRemoteLoaded) {
                ScreenLogger.log("KRONOS", "⚠️ Ya se había cargado la nube. Omitiendo.")
                return@withContext
            }
            
            try {
                ScreenLogger.log("KRONOS", "☁️ Descargando Manifest...")
                val jsonStr = URL(manifestUrl).readText()
                ScreenLogger.log("KRONOS", "✅ Manifest descargado. Tamaño: ${jsonStr.length} chars")
                
                val json = JSONObject(jsonStr)

                if (json.has("scripts")) {
                    val scripts = json.getJSONArray("scripts")
                    ScreenLogger.log("KRONOS", "📜 Encontrados ${scripts.length()} scripts")
                    for (i in 0 until scripts.length()) {
                        val scriptUrl = scripts.getString(i)
                        ScreenLogger.log("KRONOS", "⬇️ Bajando script: $scriptUrl")
                        ScriptEngine.loadScriptFromUrl(scriptUrl)
                    }
                }

                if (json.has("providers")) {
                    val remoteList = json.getJSONArray("providers")
                    ScreenLogger.log("KRONOS", "⚙️ Configurando ${remoteList.length()} proveedores")
                    for (i in 0 until remoteList.length()) {
                        val p = remoteList.getJSONObject(i)
                        val id = p.getString("id")
                        val name = p.getString("name")
                        
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
                e.printStackTrace()
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
                    ScreenLogger.log("KRONOS", "💿 JS Local (Assets) Inyectado")

                    val providerId = "sololatino"
                    if (providers.none { it.name == providerId }) {
                        val localJsProvider = JsContentProvider(providerId, "SoloLatino (Local)")
                        providers.add(localJsProvider)
                        if (remoteProviders.none { it.name == providerId }) {
                            remoteProviders.add(localJsProvider)
                        }
                        ScreenLogger.log("KRONOS", "💿 Provider Local Registrado")
                    }
                }
            } catch (e: java.io.FileNotFoundException) {
                ScreenLogger.log("ALERTA", "⚠️ No existe 'sololatino.js' en assets (Modo Nube puro)")
            } catch (e: Exception) {
                ScreenLogger.log("ERROR", "❌ Error Asset Local: ${e.message}")
            }
        }
    }

    suspend fun getLinks(
        tmdbId: Int, 
        title: String, 
        originalTitle: String, 
        isMovie: Boolean, 
        year: Int,             
        season: Int = 0, 
        episode: Int = 0
    ): List<SourceLink> = withContext(Dispatchers.IO) {
        
        ScreenLogger.log("KRONOS", "🔍 Buscando: $title ($year)")
        val allLinks = mutableListOf<SourceLink>()
        
        if (providers.isEmpty()) {
            ScreenLogger.log("ERROR", "❌ NO HAY PROVEEDORES CARGADOS")
        }

        for (provider in providers) {
            try {
                ScreenLogger.log("KRONOS", "👉 Consultando a: ${provider.name}")
                val links = if (isMovie) {
                    provider.getMovieLinks(tmdbId, title, originalTitle, year)
                } else {
                    provider.getEpisodeLinks(tmdbId, title, season, episode)
                }
                ScreenLogger.log("KRONOS", "✅ ${provider.name}: ${links.size} enlaces")
                allLinks.addAll(links)
            } catch (e: Exception) {
                ScreenLogger.log("ERROR", "❌ Fallo ${provider.name}: ${e.message}")
            }
        }
        return@withContext allLinks.sortedByDescending { it.quality }
    }
    
    suspend fun resolveVideoLink(serverName: String, originalUrl: String): String {
        return originalUrl 
    }
}
