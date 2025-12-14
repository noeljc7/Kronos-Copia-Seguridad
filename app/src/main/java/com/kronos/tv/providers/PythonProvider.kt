package com.kronos.tv.providers

import android.content.Context
import com.chaquo.python.Python
import com.kronos.tv.models.SearchResult
import com.kronos.tv.ui.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Ahora pasamos el nombre del módulo (ej: "sololatino") al constructor
class PythonProvider(
    private val context: Context, 
    private val moduleName: String = "sololatino"
) : KronosProvider {

    override val name = moduleName.replace("_", " ").capitalize()
    override val language = "Multi"

    private val python by lazy { Python.getInstance() }
    private val loaderModule by lazy { python.getModule("loader") }

    // --- BÚSQUEDA ---
    override suspend fun search(query: String): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            // Verificar si tenemos el plugin, si no, intentar bajarlo (fallback simple)
            if (!PluginRepository.isPluginInstalled(context, moduleName)) {
                AppLogger.log("PROV", "⚠️ Plugin $moduleName no encontrado. Intentando descargar...")
                PluginRepository.updatePlugin(context, moduleName)
            }

            // Llamamos a loader.py -> run_plugin_method
            val jsonResult = callPython("search", query)
            parseSearchResults(jsonResult)
        }
    }

    // --- OBTENER LINKS ---
    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        // Para simplificar, asumiremos que el plugin tiene un método "get_links" que toma una URL.
        // En un sistema real, primero buscaríamos (search) para obtener la URL del sitio.
        // Aquí simulamos que pasamos el título para que el scraper busque y resuelva.
        return withContext(Dispatchers.IO) {
             // NOTA: Aquí deberíamos pasar la URL que obtuvimos en search(), 
             // pero como ejemplo pasamos el titulo para que el python haga search+extract interno si quiere.
             // O ajustamos tu interfaz.
             emptyList() 
             // *PENDIENTE*: Ajustar lógica de flujo. Normalmente Search -> Result.url -> GetLinks(Result.url)
        }
    }
    
    // Método auxiliar para llamar a enlaces dado una URL (que viene de search)
    suspend fun resolveUrl(url: String): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val jsonResult = callPython("get_links", url)
            parseSourceLinks(jsonResult)
        }
    }

    private fun callPython(method: String, arg: String): String {
        return try {
            val result = loaderModule.callAttr("run_plugin_method", moduleName, method, arg)
            result?.toString() ?: "[]"
        } catch (e: Exception) {
            AppLogger.log("PY_ERR", "Error en llamada Python: ${e.message}")
            "[]"
        }
    }
    
    // ... (Tus métodos de parseo JSON parseSearchResults y parseSourceLinks se mantienen igual) ...
    // COPIA AQUÍ TUS MÉTODOS PRIVADOS parseSearchResults y parseSourceLinks DEL CÓDIGO ANTERIOR
    private fun parseSearchResults(json: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        try {
            if (json.contains("error")) { AppLogger.log("JSON", json); return list }
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(SearchResult(
                    title = obj.optString("title"),
                    url = obj.optString("url"),
                    img = obj.optString("img"),
                    year = obj.optString("year"),
                    type = obj.optString("type"),
                    id = obj.optString("url")
                ))
            }
        } catch (e: Exception) { }
        return list
    }

    private fun parseSourceLinks(json: String): List<SourceLink> {
        val list = mutableListOf<SourceLink>()
        try {
            if (json.contains("error")) return list
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(SourceLink(
                    name = obj.optString("server"),
                    url = obj.optString("url"),
                    quality = obj.optString("quality"),
                    language = obj.optString("lang"),
                    provider = moduleName,
                    isDirect = false // Ajustar según scraper
                ))
            }
        } catch (e: Exception) { }
        return list
    }
    
    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int) = emptyList<SourceLink>()
    override suspend fun loadEpisodes(url: String) = emptyList<com.kronos.tv.models.Episode>()
    override suspend fun loadStream(id: String, type: String) = null
}
