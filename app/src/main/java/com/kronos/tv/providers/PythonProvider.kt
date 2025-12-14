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
    private val scraperModule = try {
        python.getModule("scraper")
    } catch (e: Exception) {
        ScreenLogger.log("ERROR", "No se encontró scraper.py: ${e.message}")
        null
    }

    // --- PELÍCULAS ---
    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            // Buscamos SOLO por nombre. El año lo verificamos después.
            val bestMatch = smartSearch(title, originalTitle, year, "movie")
            
            if (bestMatch == null) {
                ScreenLogger.log("KRONOS", "⛔ No se encontró la película '$title' ($year)")
                return@withContext emptyList()
            }

            ScreenLogger.log("KRONOS", "🎯 Encontrado: '${bestMatch.title}' (Año detectado: ${bestMatch.year})")
            resolveUrl(bestMatch.url ?: "")
        }
    }

    // --- SERIES ---
    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val bestMatch = smartSearch(showTitle, showTitle, 0, "tv")
            
            if (bestMatch == null) {
                ScreenLogger.log("KRONOS", "❌ No se encontró la serie '$showTitle'")
                return@withContext emptyList()
            }

            // Convertir URL de serie a episodio
            val showUrl = bestMatch.url ?: ""
            if (showUrl.isEmpty()) return@withContext emptyList()

            var slug = showUrl
                .replace("/series/", "/episodios/")
                .replace("/tvshows/", "/episodios/")
                .trimEnd('/')

            val episodeUrl = "$slug-${season}x$episode/"
            ScreenLogger.log("KRONOS", "📺 URL Generada: $episodeUrl")
            resolveUrl(episodeUrl)
        }
    }

    // --- BÚSQUEDA Y FILTRADO ---
    private suspend fun smartSearch(title: String, originalTitle: String, year: Int, type: String): SearchResult? {
        val cleanTitle = cleanString(title)
        val cleanOriginal = cleanString(originalTitle)
        
        val allCandidates = mutableListOf<SearchResult>()

        // 1. Buscar Título en Español (SOLO TÍTULO)
        ScreenLogger.log("KRONOS", "🔎 Buscando: '$cleanTitle'")
        allCandidates.addAll(searchInternal(cleanTitle))

        // 2. Buscar Título Original (Si es diferente y no es asiático)
        if (cleanOriginal.isNotEmpty() && cleanOriginal != cleanTitle && !isAsianText(cleanOriginal)) {
            ScreenLogger.log("KRONOS", "🔎 Buscando Original: '$cleanOriginal'")
            allCandidates.addAll(searchInternal(cleanOriginal))
        }

        if (allCandidates.isEmpty()) return null

        val targetEs = normalize(title)
        val targetEn = normalize(originalTitle)
        
        // Filtramos candidatos
        val validCandidates = allCandidates
            .distinctBy { it.url }
            .filter { 
                it.type == type || 
                (type == "tv" && it.url?.contains("/series/") == true) || 
                (type == "movie" && it.url?.contains("/peliculas/") == true)
            }

        // --- AQUÍ OCURRE EL FILTRADO POR AÑO ---
        return validCandidates.minByOrNull { cand ->
            val currentTitle = normalize(cand.title ?: "")
            // El año viene del HTML que parseó Python
            val candYear = cand.year?.toIntOrNull() ?: 0
            
            var score = 100
            
            // Nombre
            if (currentTitle.contains(targetEs) || targetEs.contains(currentTitle)) score -= 50
            if (currentTitle.contains(targetEn) || targetEn.contains(currentTitle)) score -= 50
            
            // Año
            if (type == "movie" && year > 0 && candYear > 0) {
                val diff = abs(year - candYear)
                if (diff <= 1) {
                    score -= 40 // Año coincide (+/- 1) -> PREMIO
                } else {
                    score += 500 // Año no coincide -> CASTIGO (Descartar)
                }
            }
            score
        }
    }

    private fun resolveUrl(url: String): List<SourceLink> {
        if (scraperModule == null) return emptyList()
        return try {
            val pyObject = scraperModule.callAttr("get_links", url)
            val jsonStr = pyObject?.toString() ?: "[]"
            parseSourceLinks(jsonStr)
        } catch (e: Exception) { emptyList() }
    }

    private fun searchInternal(query: String): List<SearchResult> {
        if (scraperModule == null) return emptyList()
        return try {
            val pyObject = scraperModule.callAttr("search", query)
            val jsonStr = pyObject?.toString() ?: "[]"
            parseSearchResults(jsonStr)
        } catch (e: Exception) { emptyList() }
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

    private fun cleanString(str: String): String = str.replace(":", "").replace("-", " ").trim()

    private fun isAsianText(str: String): Boolean {
        for (char in str) {
            if (Character.UnicodeBlock.of(char) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) return true
        }
        return false
    }

    override suspend fun search(query: String) = searchInternal(query)
    override suspend fun loadEpisodes(url: String) = emptyList<Episode>()
    override suspend fun loadStream(id: String, type: String) = null
}
