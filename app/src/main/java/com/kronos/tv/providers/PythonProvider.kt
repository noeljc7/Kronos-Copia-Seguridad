package com.kronos.tv.providers

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.kronos.tv.ScreenLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class PythonProvider(context: Context) : KronosProvider {

    override val name = "SoloLatino (Python)"
    override val language = "Latino"

    // Inicialización del motor Python
    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    private val python = Python.getInstance()
    private val scraperModule = python.getModule("scraper") // Nombre de tu archivo .py

    override suspend fun search(query: String): List<com.kronos.tv.models.SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                // Llamamos a la función 'search' de Python
                val jsonStr = scraperModule.callAttr("search", query).toString()
                parseSearchResults(jsonStr)
            } catch (e: Exception) {
                ScreenLogger.log("PYTHON_ERR", e.message ?: "Error desconocido")
                emptyList()
            }
        }
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            // 1. Primero buscamos la serie para obtener la URL
            // (Simplificado: asumimos que search encuentra la serie correcta primero)
            val searchResults = search(showTitle)
            val series = searchResults.firstOrNull() ?: return@withContext emptyList()
            
            // 2. Construimos la URL del episodio (o dejamos que Python lo busque)
            // Para simplificar, mandaremos la URL de la serie y que Python busque el episodio
            // Ojo: Tu script Python actual 'get_links' espera URL del episodio.
            // Ajuste rápido: URL web aproximada
            val slug = series.url // https://sololatino.net/series/titulo/
            val episodeUrl = "${slug.trimEnd('/')}-${season}x$episode/" 
            
            resolveUrl(episodeUrl)
        }
    }
    
    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
         return withContext(Dispatchers.IO) {
            val searchResults = search(title)
            val movie = searchResults.firstOrNull() ?: return@withContext emptyList()
            resolveUrl(movie.url)
         }
    }

    private fun resolveUrl(url: String): List<SourceLink> {
        return try {
            ScreenLogger.log("PYTHON", "Resolviendo: $url")
            val jsonStr = scraperModule.callAttr("get_links", url).toString()
            parseSourceLinks(jsonStr)
        } catch (e: Exception) {
            ScreenLogger.log("PYTHON_ERR", e.message ?: "")
            emptyList()
        }
    }

    // --- PARSERS JSON ---
    
    private fun parseSourceLinks(json: String): List<SourceLink> {
        val list = mutableListOf<SourceLink>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(SourceLink(
                    name = obj.optString("server"),
                    url = obj.optString("url"),
                    quality = "HD",
                    language = "Multi",
                    provider = "Python",
                    isDirect = false,
                    requiresWebView = true
                ))
            }
        } catch (e: Exception) {}
        return list
    }
    
    private fun parseSearchResults(json: String): List<com.kronos.tv.models.SearchResult> {
        val list = mutableListOf<com.kronos.tv.models.SearchResult>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(com.kronos.tv.models.SearchResult(
                    title = obj.optString("title"),
                    url = obj.optString("url"),
                    img = obj.optString("img"),
                    year = obj.optString("year"),
                    type = obj.optString("type")
                ))
            }
        } catch (e: Exception) {}
        return list
    }
    
    // Funciones legacy vacías
    override suspend fun loadEpisodes(url: String) = emptyList<com.kronos.tv.models.Episode>()
    override suspend fun loadStream(id: String, type: String): String? = null
}
