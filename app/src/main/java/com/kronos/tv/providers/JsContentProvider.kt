package com.kronos.tv.providers

import android.util.Log
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer
import kotlin.math.abs

class JsContentProvider(
    override val name: String,
    private val displayName: String
) : KronosProvider {

    override val language = "Multi"

    // --- PELÍCULAS (Ya funciona perfecto) ---
    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        val bestMatch = findBestMatch(title, originalTitle, year, "movie") ?: return emptyList()
        Log.d("KRONOS", "🎯 Película Ganadora: '${bestMatch.title}'")
        
        val jsonServers = loadStream(bestMatch.id, "movie") ?: "[]"
        return parseServers(jsonServers)
    }

    // --- SERIES (NUEVA LÓGICA) ---
    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        // 1. Buscamos la SERIE primero (Usamos año 0 o el real si lo tienes, pero el nombre suele bastar)
        // Nota: Para series, el año es 'first_air_date', a veces es mejor ser flexible con el año en series.
        Log.d("KRONOS", "📺 Buscando Serie: '$showTitle' T$season E$episode")
        
        val bestMatch = findBestMatch(showTitle, showTitle, 0, "tv") 
        
        if (bestMatch == null) {
            Log.e("KRONOS", "❌ No se encontró la serie: $showTitle")
            return emptyList()
        }

        Log.d("KRONOS", "✅ Serie Encontrada: '${bestMatch.title}'. Buscando episodio...")

        // 2. Pedimos al JS que busque el episodio DENTRO de la página de la serie
        // Le pasamos la URL de la serie y los números de S y E
        val jsonServers = loadEpisodeStream(bestMatch.id, season, episode) ?: "[]"
        
        return parseServers(jsonServers)
    }

    // --- LÓGICA DE BÚSQUEDA CENTRALIZADA (Reutilizable) ---
    private suspend fun findBestMatch(title: String, originalTitle: String, year: Int, type: String): SearchResult? {
        // FASE 1: Español
        var results = search(title)
        
        // FASE 2: Inglés
        if (results.isEmpty() && title != originalTitle) {
            results = search(originalTitle)
        }

        // FASE 3: Corta
        if (results.isEmpty() && title.contains(":")) {
            val shortTitle = title.substringBefore(":").trim()
            if (shortTitle.length > 3) results = search(shortTitle)
        }

        if (results.isEmpty()) return null

        // FILTRADO INTELIGENTE
        val targetEs = normalize(title)
        val targetEn = normalize(originalTitle)
        
        return results.filter { it.type == type }.minByOrNull { cand ->
            val current = normalize(cand.title ?: "")
            val cYear = cand.year?.toIntOrNull() ?: 0
            
            var score = 1000
            if (current == targetEs || current == targetEn) score = 0
            else if (current.contains(targetEs) || targetEs.contains(current)) score = 10
            else if (current.contains(targetEn) || targetEn.contains(current)) score = 10
            
            // Filtro de año (Solo si se provee y es válido)
            if (year > 0 && cYear > 0) {
                val diff = abs(year - cYear)
                score += when (diff) {
                    0 -> 0; 1 -> 5; else -> 500
                }
            }
            score
        }
    }

    // --- PUENTES CON JS ---

    private suspend fun loadEpisodeStream(url: String, season: Int, episode: Int): String? = withContext(Dispatchers.IO) {
        // Nueva función en JS para episodios
        val res = ScriptEngine.queryProvider(name, "resolveEpisode", arrayOf(url, season, episode))
        return@withContext if (res != "null" && res != null) res.trim() else null
    }

    override suspend fun loadStream(id: String, type: String): String? = withContext(Dispatchers.IO) {
        val res = ScriptEngine.queryProvider(name, "resolveVideo", arrayOf(id, type))
        return@withContext if (res != "null" && res != null) res.trim() else null
    }

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val json = ScriptEngine.queryProvider(name, "search", arrayOf(query))
        return@withContext parseResults(json ?: "[]")
    }

    // ... (Resto de métodos auxiliares iguales: parseServers, normalize, parseResults, loadEpisodes) ...
    // Asegúrate de incluir parseServers, normalize, etc. del código anterior.
    
    private fun parseServers(json: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val s = array.getJSONObject(i)
                val rawName = s.optString("server", "Server")
                val sName = rawName.replaceFirstChar { it.uppercase() } 
                val isWeb = sName.contains("Waaw") || sName.contains("Filemoon") || sName.contains("Voe") || sName.contains("Streamwish")
                
                links.add(SourceLink(
                    name = sName,
                    url = s.optString("url"),
                    quality = "HD",
                    language = s.optString("lang", "Latino"),
                    provider = displayName,
                    isDirect = !isWeb, 
                    requiresWebView = isWeb
                ))
            }
        } catch (e: Exception) { Log.e("KRONOS", "Error parsing: $e") }
        return links
    }

    private fun normalize(str: String): String = Normalizer.normalize(str, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().replace(Regex("[^a-z0-9]"), "")

    override suspend fun loadEpisodes(url: String): List<Episode> = emptyList() 

    private fun parseResults(json: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        try {
            val clean = if (json.trim().startsWith("{")) "[]" else json
            val array = JSONArray(clean)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(SearchResult(
                    title = o.optString("title"),
                    url = o.optString("url"),
                    img = o.optString("img"),
                    id = o.optString("id"),
                    type = o.optString("type"),
                    year = o.optString("year")
                ))
            }
        } catch (e: Exception) {}
        return list
    }
}
