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

    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        Log.d("KRONOS", "🎬 Buscando: '$title' ($year) | Original: '$originalTitle'")
        
        // FASE 1: Español
        var results = search(title)
        
        // FASE 2: Inglés (Fallback)
        if (results.isEmpty() && title != originalTitle) {
            Log.w("KRONOS", "⚠️ Sin resultados ES. Probando EN: '$originalTitle'")
            results = search(originalTitle)
        }

        // FASE 3: Búsqueda Corta (Chainsaw Man strategy)
        if (results.isEmpty() && title.contains(":")) {
            val shortTitle = title.substringBefore(":").trim()
            if (shortTitle.length > 3) {
                Log.w("KRONOS", "⚠️ Probando título corto: '$shortTitle'")
                results = search(shortTitle)
            }
        }

        if (results.isEmpty()) return emptyList()

        // SELECCIÓN INTELIGENTE
        val targetEs = normalize(title)
        val targetEn = normalize(originalTitle)
        
        val bestMatch = results.filter { it.type == "movie" }.minByOrNull { cand ->
            val current = normalize(cand.title ?: "")
            val cYear = cand.year?.toIntOrNull() ?: 0
            
            var score = 1000
            // Nombre
            if (current == targetEs || current == targetEn) score = 0
            else if (current.contains(targetEs) || targetEs.contains(current)) score = 10
            else if (current.contains(targetEn) || targetEn.contains(current)) score = 10
            
            // Año (Filtro Chainsaw Man / Remakes)
            if (year > 0 && cYear > 0) {
                val diff = abs(year - cYear)
                score += when (diff) {
                    0 -> 0
                    1 -> 5
                    else -> 500 // Penalización fatal
                }
            }
            score
        }

        if (bestMatch == null) return emptyList()
        
        // Validación final de año
        val winYear = bestMatch.year?.toIntOrNull() ?: 0
        if (year > 0 && winYear > 0 && abs(year - winYear) > 2) {
            Log.w("KRONOS", "⛔ BLOQUEADO por Año: Buscado $year vs Encontrado $winYear")
            return emptyList()
        }

        Log.d("KRONOS", "🎯 Ganador: '${bestMatch.title}'")

        val jsonServers = loadStream(bestMatch.id, "movie") ?: "[]"
        return parseServers(jsonServers)
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return emptyList() // Pendiente para la siguiente fase
    }

    private fun parseServers(json: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val s = array.getJSONObject(i)
                val rawName = s.optString("server", "Server")
                // Capitalizar nombre (waaw -> Waaw)
                val sName = rawName.replaceFirstChar { it.uppercase() } 
                
                // Detectar si requiere WebView (evitar error CORS/Player en Exo)
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

    // --- UTILS ---
    private fun normalize(str: String): String = Normalizer.normalize(str, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().replace(Regex("[^a-z0-9]"), "")

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val json = ScriptEngine.queryProvider(name, "search", arrayOf(query))
        return@withContext parseResults(json ?: "[]")
    }
    
    override suspend fun loadStream(id: String, type: String): String? = withContext(Dispatchers.IO) {
        val res = ScriptEngine.queryProvider(name, "resolveVideo", arrayOf(id, type))
        return@withContext if (res != "null" && res != null) res.trim() else null
    }

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
