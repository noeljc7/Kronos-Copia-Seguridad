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

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    private val python = Python.getInstance()
    private val scraperModule = python.getModule("scraper")

    override suspend fun search(query: String): List<com.kronos.tv.models.SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                // CORRECCIÓN 1: Manejo seguro de nulos
                val pyObject = scraperModule.callAttr("search", query)
                val jsonStr = pyObject?.toString() ?: "[]"
                parseSearchResults(jsonStr)
            } catch (e: Exception) {
                ScreenLogger.log("PYTHON_ERR", e.message ?: "Error desconocido")
                emptyList()
            }
        }
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val searchResults = search(showTitle)
            val series = searchResults.firstOrNull() ?: return@withContext emptyList()
            
            // CORRECCIÓN 2: Construcción segura de URL
            val slug = series.url?.trimEnd('/') ?: ""
            if (slug.isEmpty()) return@withContext emptyList()
            
            val episodeUrl = "$slug-${season}x$episode/" 
            resolveUrl(episodeUrl)
        }
    }
    
    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
         return withContext(Dispatchers.IO) {
            val searchResults = search(title)
            val movie = searchResults.firstOrNull() ?: return@withContext emptyList()
            
            val movieUrl = movie.url ?: return@withContext emptyList()
            resolveUrl(movieUrl)
         }
    }

    private fun resolveUrl(url: String): List<SourceLink> {
        return try {
            ScreenLogger.log("PYTHON", "Resolviendo: $url")
            val pyObject = scraperModule.callAttr("get_links", url)
            val jsonStr = pyObject?.toString() ?: "[]"
            parseSourceLinks(jsonStr)
        } catch (e: Exception) {
            ScreenLogger.log("PYTHON_ERR", e.message ?: "")
            emptyList()
        }
    }

    private fun parseSourceLinks(json: String): List<SourceLink> {
        val list = mutableListOf<SourceLink>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(SourceLink(
                    name = obj.optString("server", "Server"),
                    url = obj.optString("url", ""),
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
                    type = obj.optString("type"),
                    id = obj.optString("url") // Usamos URL como ID temporal
                ))
            }
        } catch (e: Exception) {}
        return list
    }
    
    override suspend fun loadEpisodes(url: String) = emptyList<com.kronos.tv.models.Episode>()
    // CORRECCIÓN 3: Implementación correcta de la interfaz (parámetros dummy)
    override suspend fun loadStream(id: String, type: String): String? = null
}
