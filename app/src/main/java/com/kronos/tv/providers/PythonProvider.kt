package com.kronos.tv.providers

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.kronos.tv.ScreenLogger
import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer
import kotlin.math.abs

class PythonProvider(context: Context) : KronosProvider {

    override val name = "SoloLatino (Python)"
    override val language = "Latino"

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    private val python = Python.getInstance()
    // Asegúrate de que tu archivo python se llame 'scraper.py' en la carpeta assets/python
    private val scraperModule = try {
        python.getModule("scraper")
    } catch (e: Exception) {
        ScreenLogger.log("ERROR", "No se encontró scraper.py: ${e.message}")
        null
    }

    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val bestMatch = findBestMatch(title, originalTitle, year, "movie")
            if (bestMatch == null) {
                ScreenLogger.log("KRONOS", "⛔ ${name}: No se encontró película compatible.")
                return@withContext emptyList()
            }
            ScreenLogger.log("KRONOS", "🎯 Match: ${bestMatch.title}")
            resolveUrl(bestMatch.url ?: "")
        }
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val bestMatch = findBestMatch(showTitle, showTitle, 0, "tv")
            if (bestMatch == null) {
                ScreenLogger.log("KRONOS", "❌ ${name}: No se encontró la serie.")
                return@withContext emptyList()
            }
            
            val showUrl = bestMatch.url ?: ""
            if (showUrl.isEmpty()) return@withContext emptyList()

            // Lógica de transformación de URL para SoloLatino
            val slug = showUrl.replace("/series/", "/episodios/").trimEnd('/')
            val episodeUrl = "$slug-${season}x$episode/"

            resolveUrl(episodeUrl)
        }
    }

    private fun resolveUrl(url: String): List<SourceLink> {
        if (scraperModule == null) return emptyList()
        return try {
            val pyObject = scraperModule.callAttr("get_links", url)
            val jsonStr = pyObject?.toString() ?: "[]"
            parseSourceLinks(jsonStr)
        } catch (e: Exception) {
            ScreenLogger.log("PYTHON_ERR", "Error en get_links: ${e.message}")
            emptyList()
        }
    }
    
    private suspend fun findBestMatch(title: String, originalTitle: String, year: Int, type: String): SearchResult? {
        // 1. PRIMER INTENTO: Búsqueda exacta (Español)
        var results = searchInternal(title)
        
        // 2. SEGUNDO INTENTO: Si falló, buscar título original (Inglés)
        // Esto es CLAVE para películas como "Fast X" vs "Rápidos y furiosos"
        if (results.isEmpty() && title != originalTitle && originalTitle.isNotEmpty()) {
            ScreenLogger.log("KRONOS", "⚠️ Falló búsqueda en ES. Intentando EN: '$originalTitle'")
            results = searchInternal(originalTitle)
        }

        if (results.isEmpty()) return null

        val targetEs = normalize(title)
        val targetEn = normalize(originalTitle)

        val candidates = results.filter { 
             it.type == type || 
             (type == "tv" && it.url?.contains("/series/") == true) || 
             (type == "movie" && it.url?.contains("/peliculas/") == true)
        }

        return candidates.minByOrNull { cand ->
            // ... (Resto de tu lógica de filtrado de año igual) ...
            val currentTitle = normalize(cand.title ?: "")
            val candYear = cand.year?.toIntOrNull() ?: 0
            var score = 100
            
            // Comparamos contra Español Y contra Inglés
            if (currentTitle.contains(targetEs) || targetEs.contains(currentTitle)) score -= 50
            if (currentTitle.contains(targetEn) || targetEn.contains(currentTitle)) score -= 50
            
            if (type == "movie" && year > 0 && candYear > 0) {
                val diff = abs(year - candYear)
                if (diff == 0) score -= 40
                if (diff > 1) score += 200
            }
            score
        }
    }
    

    private fun searchInternal(query: String): List<SearchResult> {
        if (scraperModule == null) return emptyList()
        return try {
            val pyObject = scraperModule.callAttr("search", query)
            val jsonStr = pyObject?.toString() ?: "[]"
            parseSearchResults(jsonStr)
        } catch (e: Exception) {
            ScreenLogger.log("PYTHON_ERR", "Error en search: ${e.message}")
            emptyList()
        }
    }

    private fun parseSourceLinks(json: String): List<SourceLink> {
        val list = mutableListOf<SourceLink>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val isDirect = obj.optString("url").endsWith(".mp4") || obj.optString("url").contains(".m3u8")
                
                list.add(SourceLink(
                    name = obj.optString("server", "Server"),
                    url = obj.optString("url", ""),
                    quality = obj.optString("quality", "HD"),
                    language = obj.optString("lang", "Latino"),
                    provider = "SoloLatino",
                    isDirect = isDirect,
                    requiresWebView = !isDirect
                ))
            }
        } catch (e: Exception) {}
        return list
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
        } catch (e: Exception) {}
        return list
    }

    private fun normalize(str: String): String = Normalizer.normalize(str, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().replace(Regex("[^a-z0-9]"), "")

    override suspend fun search(query: String) = searchInternal(query)
    override suspend fun loadEpisodes(url: String) = emptyList<Episode>()
    override suspend fun loadStream(id: String, type: String) = null
}

