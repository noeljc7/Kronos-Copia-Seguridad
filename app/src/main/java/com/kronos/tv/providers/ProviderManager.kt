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

    // YA NO HAY PROVEEDORES LOCALES
    // Todo vive en el Companion Object (Estático) cargado desde la nube

    companion object {
        // Única fuente de verdad
        val remoteProviders = mutableListOf<KronosProvider>()
        private var isRemoteLoaded = false

        suspend fun loadRemoteProviders(manifestUrl: String) = withContext(Dispatchers.IO) {
            // Reiniciamos la lista cada vez que cargamos para evitar duplicados si se reintenta
            remoteProviders.clear()
            isRemoteLoaded = false
            
            try {
                ScreenLogger.log("KRONOS", "☁️ Conectando a la Nave Nodriza...")
                ScreenLogger.log("KRONOS", "URL: $manifestUrl")

                val jsonStr = URL(manifestUrl).readText()
                val json = JSONObject(jsonStr)

                // 1. Cargar Scripts JS (El cerebro)
                if (json.has("scripts")) {
                    val scripts = json.getJSONArray("scripts")
                    for (i in 0 until scripts.length()) {
                        val scriptUrl = scripts.getString(i)
                        ScriptEngine.loadScriptFromUrl(scriptUrl)
                    }
                }

                // 2. Registrar Proveedores (Los obreros)
                if (json.has("providers")) {
                    val remoteList = json.getJSONArray("providers")
                    for (i in 0 until remoteList.length()) {
                        val p = remoteList.getJSONObject(i)
                        val id = p.getString("id")
                        val name = p.getString("name")
                        
                        remoteProviders.add(JsContentProvider(id, name))
                        ScreenLogger.log("KRONOS", "✅ Proveedor Activado: $name")
                    }
                }
                
                if (remoteProviders.isEmpty()) {
                    ScreenLogger.log("ALERTA", "⚠️ El manifiesto no contiene proveedores. La app está vacía.")
                } else {
                    isRemoteLoaded = true
                    ScreenLogger.log("KRONOS", "🎉 Sistema Remoto Cargado: ${remoteProviders.size} fuentes.")
                }

            } catch (e: Exception) {
                ScreenLogger.log("ERROR", "❌ ERROR FATAL DE CONEXIÓN: ${e.message}")
                ScreenLogger.log("INFO", "La app no funcionará sin acceso al manifiesto.")
            }
        }
    }

    // Ya no hay init {} ni carga local

    suspend fun getLinks(
        tmdbId: Int, 
        title: String, 
        originalTitle: String, 
        isMovie: Boolean, 
        year: Int,              
        season: Int = 0, 
        episode: Int = 0
    ): List<SourceLink> = coroutineScope {
        
        // Si no hay proveedores remotos, retornamos vacío inmediatamente
        if (remoteProviders.isEmpty()) {
            ScreenLogger.log("KRONOS", "⛔ Solicitud rechazada: No hay proveedores cargados.")
            return@coroutineScope emptyList()
        }

        ScreenLogger.log("KRONOS", "⚡ Buscando en la nube (${remoteProviders.size} fuentes)...")
        
        // Búsqueda Paralela (Optimizada)
        val deferredResults = remoteProviders.map { provider ->
            async(Dispatchers.IO) {
                try {
                    if (isMovie) {
                        provider.getMovieLinks(tmdbId, title, originalTitle, year)
                    } else {
                        provider.getEpisodeLinks(tmdbId, title, season, episode)
                    }
                } catch (e: Exception) {
                    ScreenLogger.log("ERROR", "❌ ${provider.name}: ${e.message}")
                    emptyList<SourceLink>()
                }
            }
        }

        val results = deferredResults.awaitAll().flatten()
        return@coroutineScope results.sortedByDescending { it.quality }
    }
}
