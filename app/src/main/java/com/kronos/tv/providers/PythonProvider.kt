package com.kronos.tv.providers

import android.content.Context
import com.chaquo.python.Python
import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.SourceLink
import com.kronos.tv.ui.AppLogger
import com.kronos.tv.utils.Constants
import com.kronos.tv.utils.PythonBoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PythonProvider(
    private val context: Context,
    private val moduleName: String // Ej: "sololatino"
) : KronosProvider {

    override val name = moduleName.replace("_", " ").capitalize()
    override val language = "Multi"

    // Inicialización Lazy de Python
    private val python by lazy { 
        if (!Python.isStarted()) PythonBoot.init(context)
        Python.getInstance() 
    }
    
    // Módulo ayudante interno
    private val loaderModule by lazy { python.getModule("loader") }

    // --- BÚSQUEDA ---
    override suspend fun search(query: String): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            // Verificar si tenemos el plugin instalado
            if (!PluginRepository.isPluginInstalled(context, moduleName)) {
                AppLogger.log("PROV", "⚠️ Plugin $moduleName no encontrado. Intentando descargar...")
                val success = PluginRepository.downloadPlugin(context, moduleName, Constants.PROVIDER_REPO_URL)
                if (!success) return@withContext emptyList()
            }

            // Ejecutar: sololatino.search(query)
            val jsonResult = callPluginMethod("search", query)
            parseSearchResults(jsonResult)
        }
    }

    // --- PELÍCULAS ---
    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            // Estrategia: 
            // 1. Buscamos en el plugin usando el título
            val searchResults = search(title)
            
            // 2. Filtramos el mejor resultado (Lógica simple por ahora: primer resultado que coincida año)
            // *Nota*: En una versión avanzada, moveríamos esta lógica de filtrado a Python o la haríamos más robusta aquí.
            val bestMatch = searchResults.find { it.year == year.toString() || it.year == (year-1).toString() || it.year == (year+1).toString() } 
                            ?: searchResults.firstOrNull()

            if (bestMatch == null || bestMatch.url.isNullOrEmpty()) {
                AppLogger.log("PROV", "❌ No se encontró coincidencia para: $title")
                return@withContext emptyList()
            }

            AppLogger.log("PROV", "🎯 Coincidencia encontrada: ${bestMatch.title} -> ${bestMatch.url}")
            
            // 3. Extraemos los enlaces de esa URL
            val jsonLinks = callPluginMethod("get_links", bestMatch.url)
            parseSourceLinks(jsonLinks)
        }
    }

    // --- SERIES (EPISODIOS) ---
    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
         return withContext(Dispatchers.IO) {
            // 1. Buscar la serie
            val searchResults = search(showTitle)
            val bestMatch = searchResults.firstOrNull() // Simplificado

            if (bestMatch == null || bestMatch.url.isNullOrEmpty()) return@withContext emptyList()

            // 2. Construir URL del episodio
            // *Truco*: Muchos sitios usan formato /serie/nombre-temporada-x-episodio
            // Aquí delegamos a una función de Python si existiera, o lo hacemos manual.
            // Por ahora, asumimos que el plugin tiene un método inteligente o lo haremos manual.
            
            // INTENTO: Llamar a un método "get_episode_links" en python si existe, pasando url_serie, temp, cap
            // Como tu scraper.py actual solo tiene get_links(url), necesitamos construir la URL aquí o mejorar el scraper.
            // Haremos un hack simple para sololatino:
            
            var episodeUrl = bestMatch.url.replace("/series/", "/episodios/")
            if (episodeUrl.endsWith("/")) episodeUrl = episodeUrl.dropLast(1)
            episodeUrl = "$episodeUrl-${season}x${episode}"
            
            AppLogger.log("PROV", "📺 Intentando episodio: $episodeUrl")
            
            val jsonLinks = callPluginMethod("get_links", episodeUrl)
            parseSourceLinks(jsonLinks)
        }
    }

    // --- AYUDANTES ---

    private fun callPluginMethod(method: String, arg: String): String {
        return try {
            // Llama a loader.py -> run_plugin_method("sololatino", "search", "Batman")
            val result = loaderModule.callAttr("run_plugin_method", moduleName, method, arg)
            result?.toString() ?: "[]"
        } catch (e: Exception) {
            AppLogger.log("PY_ERR", "Error ejecutando $method en $moduleName: ${e.message}")
            "[]"
        }
    }

    private fun parseSearchResults(json: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        try {
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
        } catch (e: Exception) { 
             if (!json.startsWith("[]")) AppLogger.log("JSON_ERR", "Error parseando search: $json")
        }
        return list
    }

    private fun parseSourceLinks(json: String): List<SourceLink> {
        val list = mutableListOf<SourceLink>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(SourceLink(
                    name = obj.optString("server", "Server"),
                    url = obj.optString("url"),
                    quality = obj.optString("quality", "HD"),
                    language = obj.optString("lang", "Latino"),
                    provider = moduleName,
                    isDirect = obj.optBoolean("is_direct", false)
                ))
            }
        } catch (e: Exception) { }
        return list
    }
}
